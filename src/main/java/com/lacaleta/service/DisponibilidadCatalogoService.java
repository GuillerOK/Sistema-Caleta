package com.lacaleta.service;

import com.lacaleta.model.*;
import com.lacaleta.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

/** Disponibilidad por insumos, separada del interruptor manual Producto.disponible. */
@Service
public class DisponibilidadCatalogoService {
    private final ProductoRepository productos;
    private final RecetaInsumoRepository recetas;
    private final ComboItemRepository comboItems;
    public DisponibilidadCatalogoService(ProductoRepository productos,
             RecetaInsumoRepository recetas, ComboItemRepository comboItems) {
        this.productos = productos; this.recetas = recetas; this.comboItems = comboItems;
    }

    @Transactional
    public void actualizarStockCalculado() {
        var catalogo = productos.findAll();
        if (catalogo.isEmpty()) return;
        var recetasPorProducto = new HashMap<Integer,List<RecetaInsumo>>();
        for (var linea : recetas.findByProductoIdIn(catalogo.stream().map(Producto::getId).toList()))
            recetasPorProducto.computeIfAbsent(linea.getProducto().getId(), k -> new ArrayList<>()).add(linea);
        var porId = new HashMap<Integer,Producto>();
        for (var producto : catalogo) porId.put(producto.getId(), producto);
        var cambios = new ArrayList<Producto>();
        for (var producto : catalogo) {
            var lineas = recetasPorProducto.getOrDefault(producto.getId(), List.of());
            if (lineas.isEmpty()) {
                // Una pizza sin receta no se puede ofrecer; bebidas y acompañamientos
                // mantienen su stock por unidad y combos se calculan por sus artículos.
                if (!esCombo(producto) && esPizza(producto) && !Objects.equals(producto.getStock(), 0)) {
                    producto.setStock(0);
                    cambios.add(producto);
                }
                continue;
            }
            if (esCombo(producto)) continue; // Los combos no consumen receta directa: solo componentes.
            int calculado = unidadesPorReceta(lineas);
            if (!Objects.equals(producto.getStock(), calculado)) {
                producto.setStock(calculado);
                cambios.add(producto);
            }
        }
        // Los combos con artículos se calculan por sus componentes, incluso sin receta directa.
        // Nunca activar un producto inhabilitado manualmente: disponible es el interruptor manual.
        for (var combo : catalogo) {
            if (!esCombo(combo)) continue;
            var items = comboItems.findByComboProductoIdOrderByIdAsc(combo.getId());
            if (items.isEmpty()) {
                if (!Objects.equals(combo.getStock(), 0)) {
                    combo.setStock(0);
                    if (!cambios.contains(combo)) cambios.add(combo);
                }
                continue;
            }
            int maximo = Integer.MAX_VALUE;
            boolean tieneContenido = false;
            Map<Integer, Integer> cantidadesFijas = new HashMap<>();
            for (var item : items) {
                if (item.getCantidad() == null || item.getCantidad() <= 0
                    || "NINGUNA".equalsIgnoreCase(item.getModalidad())) continue;
                tieneContenido = true;
                if ("DEFINIDA".equalsIgnoreCase(item.getModalidad())) {
                    if (item.getProductoId() == null) { maximo = 0; continue; }
                    cantidadesFijas.merge(item.getProductoId(), item.getCantidad(), Integer::sum);
                } else if ("PERSONALIZAR".equalsIgnoreCase(item.getModalidad())) {
                    int mejor = 0;
                    for (var opcion : catalogo) {
                        if (opcion.getId().equals(combo.getId()) || esCombo(opcion)
                            || !Boolean.TRUE.equals(opcion.getDisponible())
                            || opcion.getStock() == null || opcion.getStock() <= 0
                            || !coincideTipo(opcion, item.getTipoItem())
                            || !coincideTamano(opcion, item.getTamano(), item.getTipoItem())) continue;
                        mejor = Math.max(mejor, opcion.getStock());
                    }
                    maximo = Math.min(maximo, mejor / item.getCantidad());
                } else {
                    maximo = 0;
                }
            }
            for (var fijo : cantidadesFijas.entrySet()) {
                Producto componente = porId.get(fijo.getKey());
                int stock = componente == null || !Boolean.TRUE.equals(componente.getDisponible())
                    || componente.getStock() == null ? 0 : componente.getStock();
                maximo = Math.min(maximo, stock / fijo.getValue());
            }
            if (tieneContenido) {
                int calculado = maximo == Integer.MAX_VALUE ? 0 : maximo;
                if (!Objects.equals(combo.getStock(), calculado)) {
                    combo.setStock(calculado);
                    if (!cambios.contains(combo)) cambios.add(combo);
                }
            }
        }
        if (!cambios.isEmpty()) productos.saveAll(cambios);
        // No tocar disponible: false representa una inhabilitación MANUAL.
    }

    private String normalizar(String texto) {
        return java.text.Normalizer.normalize(Objects.toString(texto, ""), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private boolean esCombo(Producto p) {
        String cat = p.getCategoria() == null ? "" : normalizar(p.getCategoria().getCodigo());
        return cat.contains("combo") || cat.contains("promoc");
    }

    private boolean esPizza(Producto p) {
        String cat = p.getCategoria() == null ? "" : normalizar(p.getCategoria().getCodigo());
        return cat.contains("pizza") || cat.contains("clasica") || cat.contains("especial")
            || cat.contains("premium") || cat.contains("tradicional");
    }

    private boolean coincideTipo(Producto p, String tipo) {
        String cat = p.getCategoria() == null ? "" : normalizar(p.getCategoria().getCodigo());
        String t = normalizar(tipo);
        if (t.contains("bebida")) return cat.contains("bebida") || cat.contains("gaseosa") || cat.contains("refresco");
        if (t.contains("acompan")) return cat.contains("acompan") || cat.contains("adicional") || cat.contains("entrada");
        return !cat.contains("combo") && !cat.contains("promoc") && !cat.contains("bebida")
            && !cat.contains("gaseosa") && !cat.contains("refresco") && !cat.contains("acompan")
            && !cat.contains("adicional") && !cat.contains("entrada");
    }

    private boolean coincideTamano(Producto p, String tamano, String tipo) {
        if (tamano == null || tamano.isBlank() || normalizar(tipo).contains("acompan")) return true;
        String solicitado = normalizar(tamano).replaceAll("\\s+", "").replace(',', '.');
        String real = normalizar(p.getTamano()).replaceAll("\\s+", "").replace(',', '.');
        String nombre = normalizar(p.getNombre()).replaceAll("\\s+", "").replace(',', '.');
        if (solicitado.equals(real) || nombre.contains(solicitado)) return true;
        if (solicitado.equals("600ml")) return nombre.contains("0.6l") || real.contains("600ml");
        if (solicitado.equals("1l")) return nombre.contains("1000ml") || real.equals("1l");
        if (solicitado.equals("1.5l")) return nombre.contains("1500ml") || real.equals("1.5l");
        if (solicitado.equals("3l")) return nombre.contains("3000ml") || real.equals("3l");
        return false;
    }

    private int unidadesPorReceta(List<RecetaInsumo> lineas) {
        long unidades = Long.MAX_VALUE;
        for (var linea : lineas) {
            BigDecimal cantidad = linea.getCantidad();
            Double stock = linea.getInsumo().getStockActual();
            if (cantidad == null || cantidad.signum() <= 0 || stock == null || stock < 0) return 0;
            long posibles = BigDecimal.valueOf(stock).divide(cantidad, 0, RoundingMode.DOWN).longValue();
            unidades = Math.min(unidades, posibles);
        }
        return unidades == Long.MAX_VALUE ? 0 : (int)Math.min(Integer.MAX_VALUE, Math.max(0, unidades));
    }
}
