package com.lacaleta.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLException;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern SMTP_STATUS = Pattern.compile("[45]\\d{2}[- ]\\d\\.\\d\\.\\d");

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String remitente;

    // <--- SECCIÓN 1: AQUÍ JAVA LEE AUTOMÁTICAMENTE LA VARIABLE 'BREVO_API_KEY' QUE PUSISTE EN RENDER
    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Autowired
    private org.thymeleaf.TemplateEngine templateEngine;

    public void enviarBoleta(com.lacaleta.model.Pedido pedido, java.math.BigDecimal subtotal,
                             java.math.BigDecimal envio) {
        enviarHtml(pedido.getUsuario().getEmail(), pedido.getId(), renderizarBoleta(pedido, subtotal, envio));
    }

    public String renderizarBoleta(com.lacaleta.model.Pedido pedido, java.math.BigDecimal subtotal,
                                   java.math.BigDecimal envio) {
        var contexto = new org.thymeleaf.context.Context(Locale.forLanguageTag("es-PE"));
        contexto.setVariable("pedido", pedido);
        contexto.setVariable("fecha", pedido.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        contexto.setVariable("items", pedido.getDetalles());
        contexto.setVariable("subtotal", pedido.getSubtotal() != null ? pedido.getSubtotal() : subtotal);
        contexto.setVariable("envio", pedido.getEnvio() != null ? pedido.getEnvio() : envio);
        return templateEngine.process("boleta-email", contexto);
    }

    public void enviarHtml(String destinatario, Integer pedidoId, String html) {
        enviarHtmlConAsunto(destinatario, "La Caleta - Boleta del pedido #" + pedidoId, html);
    }

    private void adjuntarLogoSiDisponible(org.springframework.mail.javamail.MimeMessageHelper helper)
            throws jakarta.mail.MessagingException {
        var logo = new org.springframework.core.io.ClassPathResource("static/images/lacaleta.png");
        if (logo.exists()) {
            helper.addInline("logoCaleta", logo, "image/png");
        } else {
            log.warn("No se encontró el logo del correo en static/images/lacaleta.png");
        }
    }

    private void registrarErrorEnvio(Exception error) {
        Throwable detalle = error;
        if (error instanceof MailSendException envio && envio.getMessageExceptions().length > 0)
            detalle = envio.getMessageExceptions()[0];
        int profundidad = 0;
        while (detalle.getCause() != null && detalle.getCause() != detalle && profundidad++ < 20)
            detalle = detalle.getCause();
        var status = SMTP_STATUS.matcher(String.valueOf(detalle.getMessage()));
        log.error("FALLO_CORREO motivo={}, tipo={}, causa={}, codigoSMTP={}",
                identificarMotivo(error, 0), error.getClass().getSimpleName(),
                detalle.getClass().getSimpleName(), status.find() ? status.group() : "sin codigo", error);
    }

    public String renderizarCancelacion(com.lacaleta.model.Pedido pedido) {
        var contexto = new org.thymeleaf.context.Context(Locale.forLanguageTag("es-PE"));
        contexto.setVariable("pedido", pedido);
        contexto.setVariable("fecha", pedido.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        contexto.setVariable("items", pedido.getDetalles());
        contexto.setVariable("subtotal", pedido.getSubtotal());
        contexto.setVariable("envio", pedido.getEnvio());
        return templateEngine.process("cancelacion-email", contexto);
    }

    public void enviarConfirmacionRealizado(String destinatario, Integer pedidoId) {
        String html = "<!doctype html><html lang='es'><head><meta charset='UTF-8'>"
            + "<meta name='color-scheme' content='light dark'><meta name='supported-color-schemes' content='light dark'>"
            + "<style>@media(prefers-color-scheme:dark){.caleta-body{background:#171717!important;color:#f8fafc!important}"
            + ".caleta-panel{background:#242424!important;color:#f8fafc!important}"
            + ".caleta-muted{color:#d1d5db!important}}</style></head>"
            + "<body class='caleta-body' style='margin:0;padding:24px 12px;background:#f6f3ef;color:#242424;font-family:Arial,sans-serif'>"
            + "<div class='caleta-panel' style='max-width:460px;margin:auto;background:#ffffff;color:#242424;padding:24px;border-radius:12px;text-align:center'>"
            + "<h1 style='color:#e45b47;margin:0 0 12px'>La Caleta</h1>"
            + "<div style='font-size:42px;color:#16a34a'>&#10004;</div>"
            + "<h2 style='margin:12px 0'>¡Tu pedido ha sido realizado con éxito!</h2>"
            + "<p class='caleta-muted'>Pedido #" + pedidoId + " confirmado.</p>"
            + "<p class='caleta-muted' style='font-size:13px'>Gracias por elegir La Caleta.</p>"
            + "</div></body></html>";
        enviarHtmlConAsunto(destinatario, "La Caleta - Pedido realizado #" + pedidoId, html);
    }

    private void enviarHtmlConAsunto(String destinatario, String asunto, String html) {
        // <--- SECCIÓN 2A: SI HAY API KEY DE BREVO, ENVÍA POR HTTPS (BOLETAS Y CONFIRMACIONES)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            enviarPorBrevoApi(destinatario, asunto, html, true);
            return;
        }
        if (mailSender == null) throw new CorreoException(CorreoException.Motivo.CONFIGURACION, null);
        try {
            var mensaje = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mensaje, true, "UTF-8");
            if (remitente != null && !remitente.isBlank()) helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(mensaje);
        } catch (Exception error) {
            registrarErrorEnvio(error);
            throw new CorreoException(identificarMotivo(error, 0), error);
        }
    }

    public boolean enviarCodigoVerificacion(String destino, String codigo) {
        return enviarMensaje(destino, "La Caleta - Verifica tu cuenta",
                "Hola,\n\nTu código para activar tu cuenta en La Caleta es: "
                    + codigo + "\n\nVence en 10 minutos y solo puede utilizarse una vez."
                    + "\n\nSi no creaste esta cuenta, ignora este mensaje.");
    }

    public boolean enviarCodigoRecuperacion(String destino, String codigo) {
        return enviarMensaje(destino, "La Caleta - Código de Recuperación de Contraseña",
                "Hola,\n\nTu código de verificación para restablecer tu contraseña en La Caleta es: "
                        + codigo + "\n\nEste código vencerá en 10 minutos.\n\nSi no solicitaste este cambio, ignora este mensaje.");
    }

    private boolean enviarMensaje(String destino, String asunto, String contenido) {
        // <--- SECCIÓN 2B: SI HAY API KEY DE BREVO, ENVÍA POR HTTPS (CÓDIGOS DE VERIFICACIÓN Y RECUPERACIÓN)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            enviarPorBrevoApi(destino, asunto, contenido, false);
            return true;
        }
        if (mailSender == null) {
            log.error("CORREO_CONFIGURACION: no existe un JavaMailSender en la aplicación.");
            throw new CorreoException(CorreoException.Motivo.CONFIGURACION, null);
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            if (remitente != null && !remitente.isBlank()) {
                mensaje.setFrom(remitente);
            }
            mensaje.setTo(destino);
            mensaje.setSubject(asunto);
            mensaje.setText(contenido);
            mailSender.send(mensaje);
            return true;
        } catch (Exception e) {
            CorreoException.Motivo motivo = identificarMotivo(e, 0);
            Throwable detalle = e;
            if (e instanceof MailSendException envio && envio.getMessageExceptions().length > 0) {
                detalle = envio.getMessageExceptions()[0];
            }
            while (detalle.getCause() != null && detalle.getCause() != detalle) detalle = detalle.getCause();
            var status = SMTP_STATUS.matcher(String.valueOf(detalle.getMessage()));
            log.error("CORREO_{}: tipo={}, causa={}, SMTP={}", motivo,
                    e.getClass().getSimpleName(), detalle.getClass().getSimpleName(),
                    status.find() ? status.group() : "sin código de respuesta");
            throw new CorreoException(motivo, e);
        }
    }

    // <--- SECCIÓN 3: MÉTODO QUE USA LA API KEY PARA ENVIAR POR BREVO SIN BLOQUEO DE PUERTOS
    private void enviarPorBrevoApi(String destinatario, String asunto, String cuerpo, boolean esHtml) {
        try {
            String emailRemitente = (remitente != null && !remitente.isBlank())
                    ? remitente.trim()
                    : "soporte.lacaleta@gmail.com";

            String campoContenido = esHtml ? "htmlContent" : "textContent";
            String jsonBody = "{"
                    + "\"sender\":{\"name\":\"La Caleta Pizzeria\",\"email\":\"" + escaparJson(emailRemitente) + "\"},"
                    + "\"to\":[{\"email\":\"" + escaparJson(destinatario.trim()) + "\"}],"
                    + "\"subject\":\"" + escaparJson(asunto) + "\","
                    + "\"" + campoContenido + "\":\"" + escaparJson(cuerpo) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey.trim()) // <--- AQUÍ SE INYECTA EL VALOR DE LA API KEY
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("FALLO_BREVO_API status={}, body={}", response.statusCode(), response.body());
                throw new CorreoException(CorreoException.Motivo.AUTENTICACION, null);
            }
        } catch (CorreoException ce) {
            throw ce;
        } catch (Exception e) {
            log.error("FALLO_CONEXION_BREVO: {}", e.getMessage());
            throw new CorreoException(CorreoException.Motivo.CONEXION, e);
        }
    }

    private String escaparJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private CorreoException.Motivo identificarMotivo(Throwable error, int profundidad) {
        if (error == null || profundidad > 12) return CorreoException.Motivo.ENVIO;
        if (error instanceof MailAuthenticationException || error instanceof AuthenticationFailedException) {
            return CorreoException.Motivo.AUTENTICACION;
        }
        if (error instanceof ConnectException || error instanceof SocketTimeoutException
                || error instanceof UnknownHostException || error instanceof SSLException) {
            return CorreoException.Motivo.CONEXION;
        }
        if (error instanceof MailParseException || (error instanceof SendFailedException fallo
                && fallo.getInvalidAddresses() != null && fallo.getInvalidAddresses().length > 0)) {
            return CorreoException.Motivo.DESTINATARIO;
        }
        String mensaje = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (mensaje.contains("quota") || mensaje.contains("daily user sending limit")
                || mensaje.contains("rate limit")) return CorreoException.Motivo.LIMITE;
        if (error instanceof MailSendException envio) {
            for (Exception interno : envio.getMessageExceptions()) {
                CorreoException.Motivo motivo = identificarMotivo(interno, profundidad + 1);
                if (motivo != CorreoException.Motivo.ENVIO) return motivo;
            }
        }
        return identificarMotivo(error.getCause(), profundidad + 1);
    }
}
