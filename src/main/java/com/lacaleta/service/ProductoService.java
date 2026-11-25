package com.lacaleta.service;

import com.lacaleta.model.Categoria;
import com.lacaleta.model.Producto;
import com.lacaleta.repository.ProductoRepository;
import com.lacaleta.repository.ComboRepository;
import com.lacaleta.model.Combo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private com.lacaleta.repository.RecetaInsumoRepository recetaInsumoRepository;

    @Autowired
    private com.lacaleta.repository.ComboItemRepository comboItemRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private DisponibilidadCatalogoService disponibilidadCatalogo;

    private volatile long ultimaSincronizacionStock = 0;
    private static final long INTERVALO_MINIMO_STOCK_MS = 25000;

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 20000)
    public void sincronizarStock() {
        disponibilidadCatalogo.actualizarStockCalculado();
        ultimaSincronizacionStock = System.currentTimeMillis();
    }

    public void sincronizarStockSiNecesario() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaSincronizacionStock > INTERVALO_MINIMO_STOCK_MS) {
            synchronized (this) {
                if (ahora - ultimaSincronizacionStock > INTERVALO_MINIMO_STOCK_MS) {
                    disponibilidadCatalogo.actualizarStockCalculado();
                    ultimaSincronizacionStock = System.currentTimeMillis();
                }
            }
        }
    }

    public List<Producto> listar() {
        sincronizarStockSiNecesario();
        return productoRepository.findAll();
    }

    public Optional<Producto> buscarPorId(Integer id) {
        return productoRepository.findById(id);
    }

    @Transactional
    public boolean guardar(Producto datos) {
        if (datos.getDisponible() == null) {
            datos.setDisponible(true);
        }
        if (datos.getStock() == null) {
            datos.setStock(10);
        }
        Producto guardado = productoRepository.save(datos);
        sincronizarCombo(guardado);
        return true;
    }

    @Transactional
    public Optional<Producto> actualizarDatos(Integer id, String nombre, String descripcion,
            Double precio, String imagenUrl, Categoria categoria, String tamano,
            String capacidadPersonas, Boolean disponible) {
        return productoRepository.findById(id).map(producto -> {
            producto.setNombre(nombre);
            if (descripcion != null) {
                producto.setDescripcion(descripcion);
            }
            producto.setPrecio(precio);
            if (imagenUrl != null) {
                producto.setImagenUrl(imagenUrl);
            }
            if (categoria != null) {
                producto.setCategoria(categoria);
            }
            producto.setTamano(tamano);
            producto.setCapacidadPersonas(capacidadPersonas);
            producto.setDisponible(disponible);
            Producto guardado = productoRepository.save(producto);
            sincronizarCombo(guardado);
            return guardado;
        });
    }

    private void sincronizarCombo(Producto guardado) {
        if (guardado.getCategoria() != null
                && "combos".equalsIgnoreCase(guardado.getCategoria().getCodigo())) {
            Combo combo = comboRepository.findByNombreIgnoreCase(guardado.getNombre()).orElseGet(Combo::new);
            combo.setNombre(guardado.getNombre());
            combo.setDescripcion(guardado.getDescripcion());
            combo.setPrecio(java.math.BigDecimal.valueOf(guardado.getPrecio()));
            combo.setImagenUrl(guardado.getImagenUrl());
            combo.setActivo(Boolean.TRUE.equals(guardado.getDisponible()));
            comboRepository.save(combo);
        }
    }

    @Transactional
    public boolean eliminar(Integer id) {
        if (!productoRepository.existsById(id)) return false;
        // Conservar la integridad de pedidos históricos y combos que incluyen este producto.
        Long pedidos = entityManager.createQuery(
                "select count(d) from DetallePedido d where d.producto.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (pedidos > 0 || comboItemRepository.existsByProductoId(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "El producto está asociado a pedidos o a otros combos. Inhabilítalo en lugar de eliminarlo.");
        }
        recetaInsumoRepository.deleteByProductoId(id);
        recetaInsumoRepository.flush();
        comboItemRepository.deleteByComboProductoId(id);
        comboItemRepository.flush();
        productoRepository.deleteById(id);
        productoRepository.flush();
        return true;
    }

    public List<Producto> listarConFiltros(String codigoCategoria, Boolean disponible, String buscar) {
        sincronizarStockSiNecesario();
        return productoRepository.findAll().stream()
                .filter(p -> {
                    if (codigoCategoria != null && !codigoCategoria.isBlank()) {
                        if (p.getCategoria() == null
                                || !codigoCategoria.equalsIgnoreCase(
                                        p.getCategoria().getCodigo())) {
                            return false;
                        }
                    }
                    if (disponible != null) {
                        if (!disponible.equals(p.getDisponible())) {
                            return false;
                        }
                    }
                    if (buscar != null && !buscar.isBlank()) {
                        String query = buscar.trim().toLowerCase();
                        boolean coincideNombre = p.getNombre() != null
                                && p.getNombre().toLowerCase().contains(query);
                        boolean coincideDesc = p.getDescripcion() != null
                                && p.getDescripcion().toLowerCase().contains(query);
                        return coincideNombre || coincideDesc;
                    }
                    return true;
                })
                .toList();
    }

    public List<Producto> porCategoria(String codigoCategoria) {
        sincronizarStockSiNecesario();
        return productoRepository.findAll().stream()
                .filter(this::visibleEnCarta)
                .filter(p -> p.getCategoria() != null
                        && p.getCategoria().getCodigo() != null
                        && p.getCategoria().getCodigo().trim()
                                .equalsIgnoreCase(codigoCategoria.trim()))
                .toList();
    }

    public boolean visibleEnCarta(Producto p) {
        return Boolean.TRUE.equals(p.getDisponible()) && p.getStock() != null && p.getStock() > 0;
    }

    public List<Producto> obtenerClasicas() {
        List<Producto> res = new java.util.ArrayList<>();
        res.addAll(porCategoria("principales"));
        if (res.isEmpty()) {
            res.addAll(porCategoria("clasicas"));
        }
        res.addAll(porCategoria("especiales"));
        return res.stream().filter(this::visibleEnCarta).toList();
    }

    public List<Producto> obtenerCombos() {
        List<Producto> productosCombo = porCategoria("combos");
        List<Combo> combos = comboRepository.findAll();
        if (combos.isEmpty()) return productosCombo.stream().filter(this::visibleEnCarta).toList();

        java.util.Map<String, Combo> comboMap = combos.stream()
                .filter(c -> c.getNombre() != null)
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getNombre().trim().toLowerCase(java.util.Locale.ROOT),
                        c -> c,
                        (c1, c2) -> c1
                ));

        return productosCombo.stream()
                .filter(p -> {
                    if (p.getNombre() == null) return false;
                    Combo c = comboMap.get(p.getNombre().trim().toLowerCase(java.util.Locale.ROOT));
                    return c != null && Boolean.TRUE.equals(c.getActivo());
                })
                .map(p -> {
                    Combo c = comboMap.get(p.getNombre().trim().toLowerCase(java.util.Locale.ROOT));
                    if (c != null) {
                        p.setNombre(c.getNombre());
                        p.setDescripcion(c.getDescripcion());
                        if (c.getPrecio() != null) p.setPrecio(c.getPrecio().doubleValue());
                        if (c.getImagenUrl() != null && !c.getImagenUrl().isBlank()) p.setImagenUrl(c.getImagenUrl());
                        p.setDisponible(Boolean.TRUE.equals(p.getDisponible()) && Boolean.TRUE.equals(c.getActivo()));
                    }
                    return p;
                })
                .filter(this::visibleEnCarta)
                .toList();
    }

    public Producto aplicarDatosCombo(Producto producto) {
        if (producto == null || producto.getNombre() == null || producto.getCategoria() == null
                || !"combos".equalsIgnoreCase(producto.getCategoria().getCodigo())) return producto;
        comboRepository.findByNombreIgnoreCase(producto.getNombre()).ifPresent(combo -> {
            producto.setNombre(combo.getNombre());
            producto.setDescripcion(combo.getDescripcion());
            if (combo.getPrecio() != null) producto.setPrecio(combo.getPrecio().doubleValue());
            if (combo.getImagenUrl() != null && !combo.getImagenUrl().isBlank()) producto.setImagenUrl(combo.getImagenUrl());
            // No sobreescribir el interruptor manual del administrador.
            producto.setDisponible(Boolean.TRUE.equals(producto.getDisponible()) && Boolean.TRUE.equals(combo.getActivo()));
        });
        return producto;
    }

    public List<Producto> obtenerAcompanamientos() {
        return productoRepository.findAll().stream()
                .filter(this::visibleEnCarta)
                .filter(p -> p.getCategoria() != null && p.getCategoria().getCodigo() != null)
                .filter(p -> {
                    String codigo = normalizarCodigoCategoria(p.getCategoria().getCodigo());
                    return codigo.contains("acompanamiento") || codigo.contains("adicional")
                            || codigo.contains("entrada");
                })
                .toList();
    }

    public List<Producto> obtenerBebidas() {
        return productoRepository.findAll().stream()
                .filter(this::visibleEnCarta)
                .filter(p -> p.getCategoria() != null && p.getCategoria().getCodigo() != null)
                .filter(p -> {
                    String codigo = normalizarCodigoCategoria(p.getCategoria().getCodigo());
                    return codigo.contains("bebida") || codigo.contains("gaseosa")
                            || codigo.contains("refresco");
                })
                .toList();
    }

    private String normalizarCodigoCategoria(String codigo) {
        return java.text.Normalizer.normalize(codigo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).trim();
    }

    public List<Producto> obtenerPromociones() {
        List<Producto> res = porCategoria("promociones");
        if (res.isEmpty())
            res = obtenerPromos1Persona();
        return res.stream().filter(this::visibleEnCarta).toList();
    }

    public List<Producto> obtenerPromos1Persona() {
        return obtenerPromosPorCapacidad("1 persona",
                p -> "Personal".equalsIgnoreCase(p.getTamano()));
    }

    public List<Producto> obtenerPromosDuo() {
        return obtenerPromosPorCapacidad("2 personas",
                p -> "Dúo".equalsIgnoreCase(p.getTamano()));
    }

    public List<Producto> obtenerPromosTrio() {
        return obtenerPromosPorCapacidad("3-4 personas",
                p -> "Trío".equalsIgnoreCase(p.getTamano()));
    }

    public List<Producto> obtenerPromosFamiliares() {
        return obtenerPromosPorCapacidad("5 a más", p ->
                "Familiar".equalsIgnoreCase(p.getTamano())
                        && p.getNombre() != null
                        && (p.getNombre().contains("Banquete")
                        || p.getNombre().contains("Fiesta")
                        || p.getNombre().contains("Gran Cofradía")));
    }

    private List<Producto> obtenerPromosPorCapacidad(
            String capacidadEsperada, java.util.function.Predicate<Producto> criterioLegado) {
        return productoRepository.findAll().stream()
                .filter(this::visibleEnCarta)
                .filter(producto -> {
                    String capacidad = producto.getCapacidadPersonas();
                  if (capacidad == null || capacidad.isBlank()) {
    return false;
}
                    return capacidadEsperada.equals(normalizarCapacidadPersonas(capacidad));
                })
                .toList();
    }

    private String normalizarCapacidadPersonas(String capacidad) {
        String normalizada = java.text.Normalizer.normalize(
                        capacidad, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*[-–]\\s*", "-");
        return switch (normalizada) {
            case "1 persona", "1 personas" -> "1 persona";
            case "2 persona", "2 personas" -> "2 personas";
            case "3-4 personas", "3 a 4 personas", "3/4 personas" -> "3-4 personas";
            case "5 a mas", "5 a mas personas", "5 o mas", "5 o mas personas", "5+",
                    "5+ personas" -> "5 a más";
            default -> normalizada;
        };
    }

    public List<Producto> obtenerFavoritas() {
        return obtenerClasicas().stream().limit(3).toList();
    }
}
