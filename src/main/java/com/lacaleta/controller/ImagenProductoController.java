package com.lacaleta.controller;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.util.*;

/** Sirve imágenes guardadas en Neon y conserva compatibilidad con uploads antiguos. */
@RestController
public class ImagenProductoController {
    private final JdbcTemplate jdbc;
    public ImagenProductoController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/uploads/pizzas/{archivo:.+}")
    public ResponseEntity<byte[]> imagen(@PathVariable String archivo) {
        if (!archivo.matches("[a-zA-Z0-9-]{1,80}\\.(png|jpg|jpeg)")) return ResponseEntity.notFound().build();
        String id = archivo.length() >= 36 ? archivo.substring(0,36) : archivo;
        try {
            var imagenes = jdbc.query("SELECT tipo_mime, contenido FROM imagenes_productos WHERE id = ?", (rs, n) ->
                new Imagen(rs.getString(1), rs.getBytes(2)), id);
            if (!imagenes.isEmpty()) {
                Imagen imagen = imagenes.get(0);
                return ResponseEntity.ok().contentType(MediaType.parseMediaType(imagen.tipo()))
                    .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                    .body(imagen.datos());
            }
        } catch (DataAccessException ex) {
            // Si todavía no se migraron imágenes antiguas, probar el almacenamiento previo.
        }
        try {
            Path ruta = Path.of("uploads", "pizzas", archivo).toAbsolutePath().normalize();
            if (Files.isRegularFile(ruta)) return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.endsWith(".png") ? "image/png" : "image/jpeg"))
                .body(Files.readAllBytes(ruta));
        } catch (java.io.IOException ignored) { }
        // Fotografías anteriores incluidas en el JAR: sobreviven a los reinicios de Render.
        try {
            ClassPathResource recurso = new ClassPathResource("legacy-pizzas/" + archivo);
            if (recurso.exists()) {
                MediaType tipo = archivo.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
                return ResponseEntity.ok().contentType(tipo)
                    .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)))
                    .body(recurso.getInputStream().readAllBytes());
            }
        } catch (IOException ignored) { }
        return ResponseEntity.notFound().build();
    }
    private record Imagen(String tipo, byte[] datos) { }
}
