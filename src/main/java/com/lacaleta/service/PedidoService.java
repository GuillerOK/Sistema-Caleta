package com.lacaleta.service;

import com.lacaleta.dto.CheckoutRequest;
import com.lacaleta.model.*;
import com.lacaleta.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TreeMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class PedidoService {
    public static final BigDecimal COSTO_ENVIO = new BigDecimal("5.00");
    private final ProductoRepository productos;
    private final PedidoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final BoletaOutboxService boletas;
    private final RecetaInsumoRepository recetas;
    private final InsumoRepository insumos;
    private final CuponRepository cupones;
    private final EmailService emailService;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PedidoService.class);
    @org.springframework.beans.factory.annotation.Autowired
    private ComboItemRepository comboItems;

    public PedidoService(ProductoRepository productos, PedidoRepository pedidos,
                         UsuarioRepository usuarios, BoletaOutboxService boletas,
                         RecetaInsumoRepository recetas, InsumoRepository insumos,
                         CuponRepository cupones, EmailService emailService) {
        this.productos = productos;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.boletas = boletas;
        this.recetas = recetas;
        this.insumos = insumos;
        this.cupones = cupones;
        this.emailService = emailService;
    }

    @Transactional
    public void cancelarPedido(Integer pedidoId, Integer usuarioId) {
        Pedido pedido = pedidos.bloquearPedidoCliente(pedidoId, usuarioId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pedido no encontrado."));
        if (!pedido.isCancelable()) {
            throw new ResponseStatusException(CONFLICT, "El pedido ya no puede cancelarse. Solo tienes 5 minutos desde la compra.");
        }
        // Reponer únicamente reservas efectivamente descontadas. El bloqueo del pedido
        // impide cancelaciones repetidas y una carrera con la confirmación programada.
        if (pedido.isStockReservado()) {
            try {
                var cantidades = json.readValue(pedido.getStockProductosPendiente(),
                    new com.fasterxml.jackson.core.type.TypeReference<TreeMap<Integer, Integer>>() {});
                var consumos = json.readValue(pedido.getStockInsumosPendiente(),
                    new com.fasterxml.jackson.core.type.TypeReference<TreeMap<Integer, BigDecimal>>() {});
                for (var linea : cantidades.entrySet()) {
                    Producto producto = productos.bloquearPorId(linea.getKey()).orElseThrow();
                    producto.setStock(Math.addExact(producto.getStock(), linea.getValue()));
                }
                for (var linea : consumos.entrySet()) {
                    Insumo insumo = insumos.bloquearPorId(linea.getKey()).orElseThrow();
                    insumo.setStockActual(BigDecimal.valueOf(insumo.getStockActual())
                        .add(linea.getValue()).doubleValue());
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new IllegalStateException("No se pudo restaurar el inventario del pedido", error);
            }
            pedido.setStockReservado(false);
        }
        pedido.setEstado("CANCELADO");
        pedidos.saveAndFlush(pedido);
        final Integer id = pedido.getId();
        final String correo = pedido.getUsuario().getEmail();
        final String htmlCancelacion = emailService.renderizarCancelacion(pedido);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { emailService.enviarHtml(correo, id, htmlCancelacion); }
                    catch (Exception e) { org.slf4j.LoggerFactory.getLogger(PedidoService.class).warn("Fallo aviso cancelacion pedido {}", id); }
                }
            });
    }

    @Transactional
    public void confirmarUnPedido(Integer id, LocalDateTime limite) {
        Pedido pedido = pedidos.bloquearParaConfirmacion(id).orElse(null);
        if (pedido == null || !"EN_PROCESO".equals(pedido.getEstado())
                || pedido.getFecha() == null || pedido.getFecha().isAfter(limite)) return;
        Usuario usuario = usuarios.bloquearPorId(pedido.getUsuario().getId()).orElseThrow();
        // Confirmar solo si los consumos pendientes pueden descontarse juntos.
        // Los bloqueos por ID evitan consumos simultáneos sobre el mismo stock.
        if (!pedido.isStockReservado()) try {
            // Compatibilidad: pedidos antiguos aún no tenían stock reservado.
            var tipos = new com.fasterxml.jackson.core.type.TypeReference<TreeMap<Integer, Integer>>() {};
            var tiposInsumo = new com.fasterxml.jackson.core.type.TypeReference<TreeMap<Integer, BigDecimal>>() {};
            TreeMap<Integer, Integer> pendientesProducto = pedido.getStockProductosPendiente() == null
                    ? new TreeMap<>() : json.readValue(pedido.getStockProductosPendiente(), tipos);
            TreeMap<Integer, BigDecimal> pendientesInsumo = pedido.getStockInsumosPendiente() == null
                    ? new TreeMap<>() : json.readValue(pedido.getStockInsumosPendiente(), tiposInsumo);
            for (var linea : pendientesProducto.entrySet()) {
                Producto producto = productos.bloquearPorId(linea.getKey()).orElseThrow();
                if (producto.getStock() == null || producto.getStock() < linea.getValue()) {
                    throw new IllegalStateException("Stock de producto insuficiente: " + linea.getKey());
                }
                producto.setStock(producto.getStock() - linea.getValue());
            }
            for (var linea : pendientesInsumo.entrySet()) {
                Insumo insumo = insumos.bloquearPorId(linea.getKey()).orElseThrow();
                if (insumo.getStockActual() == null || BigDecimal.valueOf(insumo.getStockActual()).compareTo(linea.getValue()) < 0) {
                    throw new IllegalStateException("Stock de insumo insuficiente: " + linea.getKey());
                }
                insumo.setStockActual(BigDecimal.valueOf(insumo.getStockActual()).subtract(linea.getValue()).doubleValue());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("No se pudo interpretar el consumo pendiente del pedido " + id, error);
        }
        // El acumulado confirmado no incluye este pedido mientras permanece EN_PROCESO.
        BigDecimal previo = BigDecimal.valueOf(pedidos.totalPorUsuario(usuario.getId()));
        BigDecimal actual = previo.add(BigDecimal.valueOf(pedido.getTotal()));
        int ganados = actual.divideToIntegralValue(new BigDecimal("5")).intValue()
            - previo.divideToIntegralValue(new BigDecimal("5")).intValue();
        usuario.setPuntos(Math.addExact(usuario.getPuntos() == null ? 0 : usuario.getPuntos(), ganados));
        pedido.setEstado("REALIZADO");
        usuarios.save(usuario);
        pedidos.saveAndFlush(pedido);
        final String correo = usuario.getEmail();
        final Integer numero = pedido.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { emailService.enviarConfirmacionRealizado(correo, numero); }
                    catch (Exception error) {
                        org.slf4j.LoggerFactory.getLogger(PedidoService.class)
                            .warn("No se pudo enviar confirmacion del pedido {}", numero);
                    }
                }
            });
    }

    @Transactional(rollbackFor = Exception.class)
    public Pedido procesarCompra(CheckoutRequest solicitud, Integer usuarioId) {
        Usuario usuario = usuarios.bloquearPorId(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Inicia sesión nuevamente."));
        if (!Boolean.TRUE.equals(usuario.getVerificado())) {
            throw new ResponseStatusException(FORBIDDEN, "Verifica tu correo antes de comprar.");
        }
        if (solicitud == null || solicitud.email() == null
                || !usuario.getEmail().equalsIgnoreCase(solicitud.email())) {
            throw new ResponseStatusException(BAD_REQUEST, "El correo no corresponde a la sesión.");
        }
        if (solicitud.items() == null || solicitud.items().isEmpty() || solicitud.items().size() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "El carrito debe contener entre 1 y 100 items.");
        }
        if (solicitud.claveCompra() == null || !solicitud.claveCompra().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new ResponseStatusException(BAD_REQUEST, "Falta una clave de compra válida.");
        }
        var existente = pedidos.findByUsuarioIdAndClaveCompra(usuarioId, solicitud.claveCompra());
        if (existente.isPresent()) {
            Pedido confirmado = existente.get();
            if (!"EN_PROCESO".equals(confirmado.getEstado()) && !"REALIZADO".equals(confirmado.getEstado()) && !"ENTREGADO".equals(confirmado.getEstado())) {
                throw new ResponseStatusException(CONFLICT, "Este intento corresponde a un pedido sin pago confirmado. Revisa tu historial.");
            }
            return confirmado;
        }
        String metodo = solicitud.metodoPago();
        if (!"ONLINE_SIMULADO".equals(metodo)) {
            throw new ResponseStatusException(BAD_REQUEST, "Completa el pago online para confirmar la compra.");
        }
        if (!"DELIVERY".equals(solicitud.tipoEntrega()) && !"RETIRO".equals(solicitud.tipoEntrega())) {
            throw new ResponseStatusException(BAD_REQUEST, "Selecciona delivery o retiro.");
        }
        boolean delivery = "DELIVERY".equals(solicitud.tipoEntrega());
        if (delivery && (solicitud.direccionEntrega() == null || solicitud.direccionEntrega().isBlank()
                || solicitud.direccionEntrega().length() > 255)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ingresa una dirección de entrega válida.");
        }
        // Agregar duplicados y bloquear siempre en orden para evitar interbloqueos.
        var cantidades = new TreeMap<Integer, Integer>();
        for (var item : solicitud.items()) {
            if (item == null || item.productoId() == null || item.productoId() <= 0
                    || item.cantidad() == null || item.cantidad() <= 0 || item.cantidad() > 1000) {
                throw new ResponseStatusException(BAD_REQUEST, "Producto o cantidad inválidos.");
            }
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }
        // Los artículos DEFINIDOS del combo también reservan sus propios insumos.
        var cantidadesAcompanamientosCombo = new TreeMap<Integer, Integer>();
        for (var entrada : cantidades.entrySet()) {
            for (ComboItem componente : comboItems.findByComboProductoIdOrderByIdAsc(entrada.getKey())) {
                if ("DEFINIDA".equalsIgnoreCase(componente.getModalidad())
                        && componente.getProductoId() != null && componente.getCantidad() != null) {
                    cantidadesAcompanamientosCombo.merge(componente.getProductoId(),
                            entrada.getValue() * componente.getCantidad(), Integer::sum);
                }
            }
        }
        // Los sabores elegidos dentro de un combo no se cobran como productos aparte,
        // pero sí consumen stock y receta igual que los artículos vendidos individualmente.
        var cantidadesPersonalizadas = new TreeMap<Integer, Integer>();
        var idsComboPersonalizado = new java.util.HashSet<Integer>();
        for (var item : solicitud.items()) {
            if (item == null || item.personalizaciones() == null) continue;
            boolean tieneContenidoCombo = item.personalizaciones().entrySet().stream()
                    .anyMatch(e -> (e.getKey().startsWith("sabores-pizza")
                            || e.getKey().startsWith("sabor-bebida")
                            || e.getKey().startsWith("acompanamiento-combo-"))
                            && e.getValue() != null && !e.getValue().isEmpty());
            if (tieneContenidoCombo && item.productoId() != null) {
                idsComboPersonalizado.add(item.productoId());
                // Los acompañamientos incluidos en el combo no se cobran por separado,
                // pero sí descuentan stock y sus recetas. La pizza/bebida se toma de la selección.
                Producto comboElegido = productos.findById(item.productoId()).orElse(null);
                String descripcionCombo = comboElegido == null || comboElegido.getDescripcion() == null
                        ? "" : comboElegido.getDescripcion().toLowerCase(java.util.Locale.ROOT);
                boolean tieneAcompanamientosConfigurados = !comboItems.findByComboProductoIdOrderByIdAsc(item.productoId()).stream()
                        .filter(ci -> "ACOMPANAMIENTO".equalsIgnoreCase(ci.getTipoItem())).toList().isEmpty();
                for (Producto componente : tieneAcompanamientosConfigurados ? List.<Producto>of() : productos.findAll()) {
                    if (componente.getCategoria() == null || componente.getCategoria().getCodigo() == null
                            || !java.util.Set.of("acompanamientos", "acompañamientos")
                                .contains(componente.getCategoria().getCodigo().toLowerCase(java.util.Locale.ROOT))) continue;
                    String nombreComponente = componente.getNombre() == null ? ""
                            : componente.getNombre().toLowerCase(java.util.Locale.ROOT);
                    boolean alitasIncluidas = descripcionCombo.contains("alitas") && nombreComponente.contains("alitas bbq");
                    boolean palitosIncluidos = descripcionCombo.contains("palitos de ajo") && nombreComponente.contains("palitos de ajo");
                    if ((alitasIncluidas || palitosIncluidos) && componente.getId() != null) {
                        cantidadesPersonalizadas.merge(componente.getId(), item.cantidad(), Integer::sum);
                    }
                }
            }
            // Los acompañamientos PERSONALIZAR se eligen en el perfil del cliente
            // y consumen stock como parte del combo, sin cobrarse de nuevo.
            for (ComboItem componente : comboItems.findByComboProductoIdOrderByIdAsc(item.productoId())) {
                if (!"ACOMPANAMIENTO".equalsIgnoreCase(componente.getTipoItem())
                        || !"PERSONALIZAR".equalsIgnoreCase(componente.getModalidad())) continue;
                var elegidos = item.personalizaciones().get("acompanamiento-combo-" + componente.getId());
                if (elegidos == null || elegidos.size() != componente.getCantidad())
                    throw new ResponseStatusException(BAD_REQUEST, "Selecciona los acompañamientos incluidos en el combo.");
                for (String elegido : elegidos) {
                    Integer idElegido;
                    try { idElegido = Integer.valueOf(elegido); }
                    catch (NumberFormatException ex) { throw new ResponseStatusException(BAD_REQUEST, "Acompañamiento inválido."); }
                    Producto acomp = productos.findById(idElegido).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Acompañamiento inexistente."));
                    if (acomp.getCategoria() == null || acomp.getCategoria().getCodigo() == null
                            || !java.text.Normalizer.normalize(acomp.getCategoria().getCodigo(), java.text.Normalizer.Form.NFD)
                                    .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).contains("acompanamiento"))
                        throw new ResponseStatusException(BAD_REQUEST, "El producto elegido no es un acompañamiento.");
                    cantidadesPersonalizadas.merge(idElegido, item.cantidad(), Integer::sum);
                }
            }
            for (String clave : item.personalizaciones().keySet()) {
                if (!(clave.equals("sabores-pizza") || clave.equals("sabor-bebida")
                        || clave.startsWith("sabores-pizza-") || clave.startsWith("sabor-bebida-"))) continue;
                var seleccionados = item.personalizaciones().get(clave);
                if (seleccionados == null) continue;
                for (String valor : seleccionados) {
                    try {
                        int idSeleccionado = Integer.parseInt(valor);
                        if (idSeleccionado > 0 && !cantidades.containsKey(idSeleccionado)) {
                            cantidadesPersonalizadas.merge(idSeleccionado, item.cantidad(), Integer::sum);
                        } else if (idSeleccionado > 0) {
                            cantidadesPersonalizadas.merge(idSeleccionado, item.cantidad(), Integer::sum);
                        }
                    } catch (NumberFormatException ignorado) {
                        // Las opciones de texto como masa y cortesías no corresponden a productos.
                    }
                }
            }
        }
        Cupon cupon = null;
        Integer regaloId = null;
        if (solicitud.codigoCupon() != null && !solicitud.codigoCupon().isBlank()) {
            cupon = cupones.bloquearPorCodigo(solicitud.codigoCupon().trim())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "El cupón no existe en la base de datos."));
            if (!"ACTIVO".equalsIgnoreCase(cupon.getEstado())) {
                throw new ResponseStatusException(BAD_REQUEST, "El cupón ya ha sido utilizado o no se encuentra activo.");
            }
            if (cupon.getFechaVencimiento() != null && cupon.getFechaVencimiento().isBefore(LocalDateTime.now())) {
                throw new ResponseStatusException(BAD_REQUEST, "El cupón ha expirado.");
            }
            if (cupon.getUsuario() == null || !cupon.getUsuario().getId().equals(usuario.getId())) {
                throw new ResponseStatusException(FORBIDDEN, "El cupón no pertenece a tu cuenta.");
            }
            if ("REGALO".equalsIgnoreCase(cupon.getTipo())) {
                if (cupon.getProductoRegalo() == null)
                    throw new ResponseStatusException(CONFLICT, "El cupón no tiene un producto de regalo configurado.");
                regaloId = cupon.getProductoRegalo().getId();
                cantidades.merge(regaloId, 1, Integer::sum);
            }
        }
        var cantidadesConsumo = new TreeMap<Integer, Integer>(cantidades);
        cantidadesPersonalizadas.forEach((id, cantidad) -> cantidadesConsumo.merge(id, cantidad, Integer::sum));
        cantidadesAcompanamientosCombo.forEach((id, cantidad) -> cantidadesConsumo.merge(id, cantidad, Integer::sum));

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setClaveCompra(solicitud.claveCompra());
        pedido.setMetodoPago(metodo);
        pedido.setDireccionEntrega(delivery ? solicitud.direccionEntrega().trim() : "RETIRO EN TIENDA");
        var productosComprados = new TreeMap<Integer, Producto>();
        var stockProductosPendiente = new TreeMap<Integer, Integer>();
        pedido.setFecha(LocalDateTime.now(ZoneId.of("America/Lima")));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var entrada : cantidades.entrySet()) {
            Producto producto = productos.bloquearPorId(entrada.getKey())
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Un producto ya no existe."));
            productosComprados.put(entrada.getKey(), producto);
            int cantidad = entrada.getValue();
            if (cantidad > 1000) throw new ResponseStatusException(BAD_REQUEST, "Máximo 1000 unidades por producto.");
            if (!Boolean.TRUE.equals(producto.getDisponible()) || producto.getStock() == null
                    || producto.getStock() < cantidad) {
                throw new ResponseStatusException(CONFLICT, "Stock insuficiente para " + producto.getNombre());
            }
            if (producto.getPrecio() == null || !Double.isFinite(producto.getPrecio()) || producto.getPrecio() < 0) {
                throw new ResponseStatusException(CONFLICT, "El producto no tiene un precio válido.");
            }
            BigDecimal precio = BigDecimal.valueOf(producto.getPrecio()).setScale(2, RoundingMode.HALF_UP);
            boolean incluyeRegalo = entrada.getKey().equals(regaloId);
            int cantidadPagada = cantidad - (incluyeRegalo ? 1 : 0);
            BigDecimal importe = precio.multiply(BigDecimal.valueOf(cantidadPagada));
            stockProductosPendiente.merge(producto.getId(), cantidad, Integer::sum);
            DetallePedido detalle = new DetallePedido(pedido, producto, cantidadPagada, precio.doubleValue());
            detalle.setSubtotal(importe.doubleValue());
            if (cantidadPagada > 0) pedido.getDetalles().add(detalle);
            if (incluyeRegalo) {
                var regalo = new DetallePedido(pedido, producto, 1, 0.0);
                regalo.setCuponRegalo(true);
                pedido.getDetalles().add(regalo);
            }
            subtotal = subtotal.add(importe);
        }
        // Las piezas definidas del combo también deben reservar stock de producto.
        for (var entrada : cantidadesAcompanamientosCombo.entrySet()) {
            stockProductosPendiente.merge(entrada.getKey(), entrada.getValue(), Integer::sum);
        }
        for (var entrada : cantidadesPersonalizadas.entrySet()) {
            Producto seleccionado = productos.bloquearPorId(entrada.getKey())
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Una opción seleccionada ya no existe."));
            if (!Boolean.TRUE.equals(seleccionado.getDisponible()) || seleccionado.getStock() == null
                    || seleccionado.getStock() < entrada.getValue()) {
                throw new ResponseStatusException(CONFLICT, "Stock insuficiente para la opción seleccionada: " + seleccionado.getNombre());
            }
            stockProductosPendiente.merge(seleccionado.getId(), entrada.getValue(), Integer::sum);
        }

        var consumos = new TreeMap<Integer, BigDecimal>();
        var conReceta = new java.util.HashSet<Integer>();
        for (var linea : recetas.findByProductoIdIn(cantidadesConsumo.keySet())) {
            // Para un combo personalizado, el consumo se calcula desde las pizzas y bebidas elegidas,
            // no desde una receta genérica asignada al combo completo.
            // Nunca consumir recetas directas de combos/promociones, incluso si son antiguas.
            // El consumo real se calcula exclusivamente con sus items seleccionados.
            String categoriaReceta = linea.getProducto().getCategoria() == null ? ""
                    : linea.getProducto().getCategoria().getCodigo();
            if (java.util.Set.of("combos", "promociones", "promocion")
                    .contains(categoriaReceta == null ? "" : categoriaReceta.toLowerCase(java.util.Locale.ROOT))) continue;
            if (linea.getCantidad() == null || linea.getCantidad().signum() <= 0) {
                throw new ResponseStatusException(CONFLICT, "Receta con cantidades inválidas.");
            }
            conReceta.add(linea.getProducto().getId());
            consumos.merge(linea.getInsumo().getId(), linea.getCantidad()
                    .multiply(BigDecimal.valueOf(cantidadesConsumo.get(linea.getProducto().getId()))), BigDecimal::add);
        }
        var productosQueConsumen = new TreeMap<Integer, Producto>(productosComprados);
        for (Integer idSeleccionado : cantidadesPersonalizadas.keySet()) {
            productos.bloquearPorId(idSeleccionado).ifPresent(p -> productosQueConsumen.put(idSeleccionado, p));
        }
        for (var producto : productosQueConsumen.values()) {
            String categoria = producto.getCategoria() == null ? "" : producto.getCategoria().getCodigo();
            if (!idsComboPersonalizado.contains(producto.getId())
                    && !java.util.Set.of("bebidas", "bebida", "combos").contains(categoria == null ? "" : categoria.toLowerCase(java.util.Locale.ROOT))
                    && !conReceta.contains(producto.getId())) {
                throw new ResponseStatusException(CONFLICT, "Configura la receta de insumos para " + producto.getNombre());
            }
        }
        for (var consumo : consumos.entrySet()) {
            Insumo insumo = insumos.bloquearPorId(consumo.getKey())
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Un insumo ya no existe."));
            Double stock = insumo.getStockActual();
            if (stock == null || !Double.isFinite(stock)
                    || BigDecimal.valueOf(stock).compareTo(consumo.getValue()) < 0) {
                throw new ResponseStatusException(CONFLICT, "Insumo insuficiente: " + insumo.getNombre());
            }
            // Reservar ahora, dentro de la misma transacción que guarda el pedido.
            insumo.setStockActual(BigDecimal.valueOf(stock).subtract(consumo.getValue()).doubleValue());
        }
        BigDecimal envio = delivery ? COSTO_ENVIO : BigDecimal.ZERO;
        BigDecimal descuentoCupon = BigDecimal.ZERO;
        if (cupon != null) {
            if (cupon.getDescuento() == null || !Double.isFinite(cupon.getDescuento())
                    || cupon.getDescuento() < 0
                    || ("PORCENTAJE".equalsIgnoreCase(cupon.getTipo()) && cupon.getDescuento() > 100)) {
                throw new ResponseStatusException(CONFLICT, "El cupón tiene un descuento inválido.");
            }
            if (cupon.getCodigo().startsWith("CAL10") && subtotal.compareTo(new BigDecimal("40.00")) < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Este cupón requiere un consumo mínimo de S/ 40.00.");
            }
            if ("DELIVERY_GRATIS".equalsIgnoreCase(cupon.getTipo())) {
                descuentoCupon = envio;
            } else if ("PORCENTAJE".equalsIgnoreCase(cupon.getTipo())) {
                descuentoCupon = subtotal.multiply(BigDecimal.valueOf(cupon.getDescuento()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if ("REGALO".equalsIgnoreCase(cupon.getTipo())) {
                descuentoCupon = BigDecimal.ZERO;
            } else if ("MONTO_FIJO".equalsIgnoreCase(cupon.getTipo())) {
                descuentoCupon = BigDecimal.valueOf(cupon.getDescuento()).setScale(2, RoundingMode.HALF_UP).min(subtotal);
            }
            else throw new ResponseStatusException(BAD_REQUEST, "Tipo de cupón no válido.");
            pedido.setCodigoCupon(cupon.getCodigo());
            pedido.setTipoCupon(cupon.getTipo());
            pedido.setTituloCupon(cupon.getTitulo());
            cupon.setEstado("USADO");
            cupones.save(cupon);
        }
        pedido.setSubtotal(subtotal.doubleValue());
        pedido.setEnvio(envio.doubleValue());
        pedido.setDescuentoCupon(descuentoCupon.doubleValue());
        BigDecimal totalFinal = subtotal.add(envio).subtract(descuentoCupon).max(BigDecimal.ZERO);
        pedido.setTotal(totalFinal.setScale(2, RoundingMode.HALF_UP).doubleValue());
        // El servidor aprueba el pago simulado tras validar importes, cupón y existencias.
        // La confirmación, el consumo del cupón y el stock se confirman juntos o se revierten.
        try {
            pedido.setStockProductosPendiente(json.writeValueAsString(stockProductosPendiente));
            pedido.setStockInsumosPendiente(json.writeValueAsString(consumos));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("No se pudo guardar el consumo pendiente", error);
        }
        // Bloqueados y validados previamente. El descuento evita sobreventa entre compras.
        for (var linea : stockProductosPendiente.entrySet()) {
            Producto producto = productos.bloquearPorId(linea.getKey()).orElseThrow();
            if (producto.getStock() == null || producto.getStock() < linea.getValue())
                throw new ResponseStatusException(CONFLICT, "Stock insuficiente para " + producto.getNombre());
            producto.setStock(producto.getStock() - linea.getValue());
        }
        pedido.setStockReservado(true);
        pedido.setEstado("EN_PROCESO");
        pedidos.saveAndFlush(pedido); // Incluye detalles por CascadeType.ALL y stock por dirty checking.

        // Los puntos se acreditan únicamente al pasar a REALIZADO.
        // La compra y la boleta pendiente se guardan en la misma transacción.
        // Un fallo de SMTP no debe impedir el pedido: el proceso programado reintentará el envío.
        boletas.encolar(pedido, subtotal, envio);
        return pedido;
    }
}
