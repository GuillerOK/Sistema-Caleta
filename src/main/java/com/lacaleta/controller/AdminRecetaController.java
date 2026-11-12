package com.lacaleta.controller;

import com.lacaleta.model.RecetaInsumo;
import com.lacaleta.repository.RecetaInsumoRepository;
import com.lacaleta.service.RecetaService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/productos/{productoId}/receta")
public class AdminRecetaController {
    private final RecetaInsumoRepository recetas;
    private final RecetaService recetaService;

    public AdminRecetaController(RecetaInsumoRepository recetas, RecetaService recetaService) {
        this.recetas = recetas;
        this.recetaService = recetaService;
    }

    public record Linea(Integer insumoId, java.math.BigDecimal cantidad) { }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Linea> consultar(@PathVariable Integer productoId) {
        return recetas.findByProductoIdIn(List.of(productoId)).stream()
                .map(r -> new Linea(r.getInsumo().getId(), r.getCantidad())).toList();
    }

    @PutMapping
    public Map<String, String> guardar(@PathVariable Integer productoId, @RequestBody List<Linea> lineas) {
        int stock = recetaService.guardar(productoId, lineas == null ? null : lineas.stream()
                .map(linea -> linea == null ? null : new RecetaService.Linea(linea.insumoId(), linea.cantidad()))
                .toList());
        return Map.of("mensaje", "Receta actualizada y stock recalculado.", "stock", String.valueOf(stock));
    }
}
