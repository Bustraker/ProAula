package com.proaula.aula.Service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Servicio encargado del despacho de notificaciones vía correo electrónico
 * para usuarios que se encuentran fuera de la aplicación.
 */
@Service
public class NotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailService.class);

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String publicBaseUrl;

    public NotificationEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String senderEmail,
            @Value("${app.public-base-url:http://localhost:5679}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean enviarNotificacionEmail(String destinatario, String titulo, String mensaje, String tipo, String nombreUsuario) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("No se puede enviar email de notificación: destinatario no especificado.");
            return false;
        }

        if (senderEmail == null || senderEmail.isBlank()) {
            log.info("Remitente de correo no configurado (spring.mail.username). Notificación simulada por correo a {}", destinatario);
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail, "Bustraker Cartagena");
            helper.setTo(destinatario);
            helper.setSubject("🔔 " + (titulo != null ? titulo : "Aviso importante") + " · Bustraker");

            String htmlBody = construirPlantillaHtml(titulo, mensaje, tipo, nombreUsuario);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Notificación enviada exitosamente por correo a {}", destinatario);
            return true;
        } catch (Exception ex) {
            log.warn("No fue posible despachar la notificación por correo a {}: {}", destinatario, ex.getMessage());
            return false;
        }
    }

    private String construirPlantillaHtml(String titulo, String mensaje, String tipo, String nombreUsuario) {
        String urlApp = publicBaseUrl.replaceAll("/+$", "") + "/index_3";
        String saludo = (nombreUsuario != null && !nombreUsuario.isBlank()) ? "Hola, " + escapeHtml(nombreUsuario) : "Hola";
        String tipoEtiqueta = (tipo != null && !tipo.isBlank()) ? escapeHtml(tipo) : "Aviso de Movilidad";
        String tituloLimpio = (titulo != null ? escapeHtml(titulo) : "Aviso de ruta");
        String mensajeLimpio = (mensaje != null ? escapeHtml(mensaje) : "");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"es\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px 12px; color: #1e293b; }\n");
        sb.append("    .email-card { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 14px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }\n");
        sb.append("    .email-header { background: linear-gradient(135deg, #071e28 0%, #0e4c5b 100%); color: #ffffff; padding: 32px 24px; text-align: center; }\n");
        sb.append("    .email-header h1 { margin: 0; font-size: 24px; font-weight: 800; letter-spacing: -0.5px; }\n");
        sb.append("    .email-badge { display: inline-block; padding: 4px 14px; border-radius: 999px; background: rgba(55, 200, 186, 0.22); color: #38bdf8; font-size: 11px; font-weight: 700; text-transform: uppercase; margin-top: 10px; letter-spacing: 0.05em; }\n");
        sb.append("    .email-body { padding: 32px 26px; line-height: 1.6; }\n");
        sb.append("    .saludo { font-size: 16px; font-weight: 600; color: #0e4c5b; margin-bottom: 8px; }\n");
        sb.append("    .notif-title { font-size: 20px; font-weight: 800; color: #0f172a; margin: 0 0 16px 0; }\n");
        sb.append("    .notif-box { background: #f0fdf4; border-left: 4px solid #10b981; padding: 16px 18px; border-radius: 8px; font-size: 15px; color: #064e3b; margin: 18px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }\n");
        sb.append("    .email-note { font-size: 13px; color: #64748b; line-height: 1.5; margin-top: 24px; }\n");
        sb.append("    .btn-wrapper { text-align: center; margin: 30px 0 10px; }\n");
        sb.append("    .email-btn { display: inline-block; background: linear-gradient(135deg, #0f766e, #14b8a6); color: #ffffff !important; text-decoration: none; padding: 13px 32px; border-radius: 10px; font-weight: 700; font-size: 15px; box-shadow: 0 4px 14px rgba(20, 184, 166, 0.35); }\n");
        sb.append("    .email-footer { background: #f8fafc; padding: 20px 24px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <div class=\"email-card\">\n");
        sb.append("    <div class=\"email-header\">\n");
        sb.append("      <h1>Bustraker Cartagena</h1>\n");
        sb.append("      <span class=\"email-badge\">").append(tipoEtiqueta).append("</span>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"email-body\">\n");
        sb.append("      <div class=\"saludo\">").append(saludo).append("</div>\n");
        sb.append("      <h2 class=\"notif-title\">").append(tituloLimpio).append("</h2>\n");
        sb.append("      <div class=\"notif-box\">\n");
        sb.append("        ").append(mensajeLimpio).append("\n");
        sb.append("      </div>\n");
        sb.append("      <p class=\"email-note\">\n");
        sb.append("        Te enviamos este mensaje a tu bandeja de correo porque te encuentras fuera de la plataforma. Para consultar el estado en tiempo real o modificar tus alertas de ruta, pulsa el siguiente botón:\n");
        sb.append("      </p>\n");
        sb.append("      <div class=\"btn-wrapper\">\n");
        sb.append("        <a href=\"").append(urlApp).append("\" class=\"email-btn\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir Bustraker</a>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"email-footer\">\n");
        sb.append("      &copy; 2026 Bustraker &middot; Sistema de Información y Monitoreo de Transporte Urbano &middot; Cartagena de Indias\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
