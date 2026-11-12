package com.lacaleta.controller;

import com.lacaleta.model.Producto;
import com.lacaleta.service.ProductoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

/** Estado público del catálogo para actualizar las vistas sin intervención del usuario. */
@RestController
public class DisponibilidadPublicaController {
    private final ProductoService productos;
    public DisponibilidadPublicaController(ProductoService productos) { this.productos = productos; }
    public record Estado(Integer id, boolean disponible, int stock) {}
    @GetMapping("/api/catalogo/disponibilidad")
    public List<Estado> consultar() {
        return productos.listar().stream()
            .filter(p -> p.getId() != null)
            .map(p -> new Estado(p.getId(), productos.visibleEnCarta(p),
                p.getStock() == null ? 0 : p.getStock()))
            .toList();
    }
}
