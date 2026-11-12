package com.lacaleta.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lacaleta.repository.PedidoRepository;

@Component
public class ConfirmacionPedidoScheduler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ConfirmacionPedidoScheduler.class);
    private final PedidoService servicio;
    private final PedidoRepository repositorio;

    public ConfirmacionPedidoScheduler(PedidoService servicio, PedidoRepository repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    @Scheduled(fixedDelay = 15000)
    public void ejecutar() {
        LocalDateTime limite = LocalDateTime.now(ZoneId.of("America/Lima")).minusMinutes(5);
        for (Integer id : repositorio.idsPorConfirmar(limite)) {
            try {
                // Invoca al proxy Spring: @Transactional aplica por cada pedido.
                servicio.confirmarUnPedido(id, limite);
            } catch (Exception e) {
                LOG.error("No se pudo confirmar el pedido {}", id, e);
            }
        }
    }
}
