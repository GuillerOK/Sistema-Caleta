package com.lacaleta.service;

import com.lacaleta.model.*;
import com.lacaleta.repository.BoletaPendienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;

@Service
public class BoletaOutboxService {
    @org.springframework.beans.factory.annotation.Value("${app.boletas.envio-habilitado:true}")
    private boolean envioHabilitado = true;
    private final BoletaPendienteRepository pendientes;
    private final EmailService correo;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BoletaOutboxService.class);
    public BoletaOutboxService(BoletaPendienteRepository pendientes, EmailService correo) {
        this.pendientes = pendientes; this.correo = correo;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void encolar(Pedido pedido, BigDecimal subtotal, BigDecimal envio) {
        String html;
        try {
            html = correo.renderizarBoleta(pedido, subtotal, envio);
        } catch (RuntimeException error) {
            // La boleta siempre queda en cola incluso si falla la plantilla.
            // El HTML de respaldo permite enviar un comprobante básico sin bloquear la compra.
            html = "<!doctype html><html lang=\"es\"><meta charset=\"UTF-8\"><body>"
                    + "<h1>La Caleta - Comprobante de compra</h1><p>Pedido #" + pedido.getId() + "</p>"
                    + "<p>Fecha: " + pedido.getFecha() + "</p><p>Total: S/ " + pedido.getTotal() + "</p>"
                    + "<p>Conserva este mensaje como constancia de tu pedido.</p></body></html>";
            log.error("No se pudo renderizar la plantilla de boleta para pedido {}. Se usará un comprobante básico.", pedido.getId());
        }
        if (html == null || html.isBlank()) {
            html = "<!doctype html><html lang=\"es\"><meta charset=\"UTF-8\"><body>"
                    + "<h1>La Caleta - Comprobante de compra</h1><p>Pedido #" + pedido.getId() + "</p>"
                    + "<p>Fecha: " + pedido.getFecha() + "</p><p>Total: S/ " + pedido.getTotal() + "</p>"
                    + "<p>Conserva este mensaje como constancia de tu pedido.</p></body></html>";
        }
        pendientes.save(new BoletaPendiente(pedido.getId(), pedido.getUsuario().getEmail(), html));
    }
    @Scheduled(fixedDelayString = "${app.boletas.intervalo-ms:10000}")
    @Transactional
    public void enviarPendientes() {
        if (!envioHabilitado) return;
        // Solo son visibles las filas de pedidos confirmados; SKIP LOCKED permite varios servidores.
        for (BoletaPendiente boleta : pendientes.bloquearPendientes()) {
            try {
                correo.enviarHtml(boleta.getDestinatario(), boleta.getPedidoId(), boleta.getHtml());
                pendientes.delete(boleta);
            } catch (CorreoException error) {
                boleta.reintentar();
                log.warn("Boleta pendiente pedido={}, intento={}", boleta.getPedidoId(), boleta.getIntentos());
            }
        }
    }
}
