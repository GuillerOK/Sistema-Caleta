package com.lacaleta.controller;

import com.lacaleta.model.Usuario;
import com.lacaleta.repository.PedidoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/cliente")
public class EstadoPedidoPollingController {
    private final PedidoRepository pedidos;
    public EstadoPedidoPollingController(PedidoRepository pedidos) { this.pedidos = pedidos; }

    @GetMapping("/estados-pedidos")
    public ResponseEntity<?> estados(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Sesión finalizada"));
        var lista = pedidos.findByUsuarioIdOrderByFechaDesc(usuario.getId()).stream()
            .map(p -> Map.of("id", p.getId(), "estado", p.getEstado() == null ? "" : p.getEstado()))
            .toList();
        return ResponseEntity.ok(lista);
    }
}
