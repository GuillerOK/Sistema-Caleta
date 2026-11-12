package com.lacaleta.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

/** Almacenamiento persistente en PostgreSQL (Render no conserva uploads locales). */
@Service
public class ImagenProductoStorage {
    private final JdbcTemplate jdbc;
    public ImagenProductoStorage(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String guardar(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) throw new IOException("Selecciona una imagen.");
        if (archivo.getSize() > 2 * 1024 * 1024) throw new IOException("Imagen demasiado grande (máximo 2 MB).");
        byte[] contenido = archivo.getBytes();
        String tipo;
        if (contenido.length > 8 && contenido[0] == (byte)0x89 && contenido[1] == 'P'
                && contenido[2] == 'N' && contenido[3] == 'G') tipo = "image/png";
        else if (contenido.length > 3 && contenido[0] == (byte)0xff && contenido[1] == (byte)0xd8
                && contenido[2] == (byte)0xff) tipo = "image/jpeg";
        else throw new IOException("Solo se permiten imágenes PNG o JPEG.");
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO imagenes_productos (id, tipo_mime, contenido) VALUES (?, ?, ?)", id, tipo, contenido);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new IOException("No se pudo guardar la imagen en Neon. Ejecuta db/imagenes_productos_neon.sql y comprueba la conexión.", ex);
        }
        return "/uploads/pizzas/" + id + (tipo.equals("image/png") ? ".png" : ".jpg");
    }

    public static boolean urlValida(String valor) {
        if (valor == null || valor.isBlank()) return true;
        String url = valor.trim();
        return !url.matches(".*[\\s\"'<>].*") && (url.startsWith("https://")
                || url.startsWith("/images/") || url.startsWith("/uploads/pizzas/"));
    }
}
