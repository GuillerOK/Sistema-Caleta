package com.lacaleta.service;

import com.lacaleta.model.Producto;
import com.lacaleta.model.RecetaInsumo;
import com.lacaleta.repository.InsumoRepository;
import com.lacaleta.repository.ProductoRepository;
import com.lacaleta.repository.RecetaInsumoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecetaService {
    private static final BigDecimal CANTIDAD_MAXIMA = new BigDecimal("1000000");

    private final ProductoRepository productos;
    private final InsumoRepository insumos;
    private final RecetaInsumoRepository recetas;
    private final com.lacaleta.repository.ComboItemRepository comboItems;

    public RecetaService(ProductoRepository productos, InsumoRepository insumos,
            RecetaInsumoRepository recetas, com.lacaleta.repository.ComboItemRepository comboItems) {
        this.productos = productos;
        this.insumos = insumos;
        this.recetas = recetas;
        this.comboItems = comboItems;
    }

    public record Linea(Integer insumoId, BigDecimal cantidad) { }

    @Transactional
    public int guardar(Integer productoId, List<Linea> lineas) {
        if (lineas == null || lineas.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La receta debe contener como máximo 100 insumos.");
        }

        Producto producto = productos.bloquearPorId(productoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto inexistente."));
        boolean esCombo = producto.getCategoria() != null
                && producto.getCategoria().getCodigo() != null
                && (producto.getCategoria().getCodigo().toLowerCase(java.util.Locale.ROOT).contains("combo")
                || producto.getCategoria().getCodigo().toLowerCase(java.util.Locale.ROOT).contains("promoc"));
        // El contenido guardado en combo_items es una receta válida por composición,
        // aunque el código de categoría no contenga literalmente "combo".
        boolean tieneArticulos = comboItems.findByComboProductoIdOrderByIdAsc(productoId).stream()
                .anyMatch(item -> !"NINGUNA".equalsIgnoreCase(item.getModalidad()));
        if (lineas.isEmpty() && !tieneArticulos) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Agrega un insumo o configura y guarda los artículos del combo.");
        }
        Set<Integer> ids = new HashSet<>();
        List<RecetaInsumo> nuevas = new ArrayList<>(lineas.size());

        for (Linea linea : lineas) {
            if (linea == null || linea.insumoId() == null || !ids.add(linea.insumoId())
                    || linea.cantidad() == null || linea.cantidad().signum() <= 0
                    || linea.cantidad().stripTrailingZeros().scale() > 2
                    || linea.cantidad().compareTo(CANTIDAD_MAXIMA) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Receta inválida o insumos duplicados.");
            }

            var insumo = insumos.findById(linea.insumoId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Insumo inexistente."));
            RecetaInsumo receta = new RecetaInsumo();
            receta.setProducto(producto);
            receta.setInsumo(insumo);
            receta.setCantidad(linea.cantidad());
            nuevas.add(receta);
        }

        recetas.deleteByProductoId(productoId);
        recetas.flush();
        List<RecetaInsumo> guardadas = recetas.saveAllAndFlush(nuevas);

        // El stock de combos sin insumos directos se calcula a partir de sus artículos.
        if (!guardadas.isEmpty()) producto.setStock(calcularStock(guardadas));
        productos.save(producto);
        return producto.getStock();
    }

    private int calcularStock(List<RecetaInsumo> lineas) {
        long posible = Long.MAX_VALUE;
        for (RecetaInsumo linea : lineas) {
            Double stockDisponible = linea.getInsumo().getStockActual();
            if (stockDisponible == null || !Double.isFinite(stockDisponible) || stockDisponible < 0) {
                return 0;
            }
            long unidades = BigDecimal.valueOf(stockDisponible)
                    .divide(linea.getCantidad(), 0, RoundingMode.FLOOR)
                    .longValue();
            posible = Math.min(posible, unidades);
        }
        return posible == Long.MAX_VALUE ? 0
                : (int) Math.max(0, Math.min(Integer.MAX_VALUE, posible));
    }
}
