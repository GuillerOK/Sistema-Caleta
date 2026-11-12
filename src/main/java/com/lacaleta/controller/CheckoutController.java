package com.lacaleta.controller;

import com.lacaleta.dto.CheckoutRequest;
import com.lacaleta.model.Usuario;
import com.lacaleta.service.PedidoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final PedidoService pedidos;
    public CheckoutController(PedidoService pedidos) { this.pedidos = pedidos; }

    @PostMapping
    public ResponseEntity<?> comprar(@RequestBody CheckoutRequest solicitud, HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Inicia sesión para comprar."));
        var pedido = pedidos.procesarCompra(solicitud, usuario.getId());
        return ResponseEntity.ok(Map.of("pedidoId", pedido.getId(), "total", pedido.getTotal(), "boleta", "ENCOLADA", "estado", pedido.getEstado()));
    }

    @PostMapping("/{pedidoId}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Integer pedidoId, HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Inicia sesión."));
        pedidos.cancelarPedido(pedidoId, usuario.getId());
        return ResponseEntity.ok(Map.of("estado", "CANCELADO", "mensaje", "Pedido cancelado correctamente."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> solicitudInvalida(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }

}
