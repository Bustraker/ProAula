package com.proaula.aula.Service;

import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Repository.AdminCodeRequestRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import com.proaula.aula.Entity.AdminCode;

@Service
public class AdminCodeRequestService {

    private final AdminCodeRequestRepository requestRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdminCodeService adminCodeService;
    private final AdminPushNotificationService pushNotificationService;
    private final UsuarioService usuarioService;
    private final JavaMailSender mailSender;
    private final String approverEmail;
    private final String senderEmail;
    private final String senderPassword;
    private final String publicBaseUrl;

    public AdminCodeRequestService(
            AdminCodeRequestRepository requestRepository,
            UsuarioRepository usuarioRepository,
            AdminCodeService adminCodeService,
            AdminPushNotificationService pushNotificationService,
            UsuarioService usuarioService,
            JavaMailSender mailSender,
            @Value("${app.admin.approver-email:}") String approverEmail,
            @Value("${spring.mail.username:}") String senderEmail,
            @Value("${spring.mail.password:}") String senderPassword,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.requestRepository = requestRepository;
        this.usuarioRepository = usuarioRepository;
        this.adminCodeService = adminCodeService;
        this.pushNotificationService = pushNotificationService;
        this.usuarioService = usuarioService;
        this.mailSender = mailSender;
        this.approverEmail = approverEmail;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public boolean createRequest(String nombres, String apellidos, String email, String motivo, String createdBy) {
        String normalizedNombres = normalizeName(nombres, "nombre");
        String normalizedApellidos = normalizeName(apellidos, "apellido");
        String normalizedEmail = normalizeEmail(email);
        String normalizedMotivo = normalizeMotivo(motivo);
        validateMailConfiguration();
        Usuario requester = findRequester(createdBy);
        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo electrónico.");
        }
        if (requestRepository.existsByEmailIgnoreCaseAndStatusIn(normalizedEmail,
                List.of(AdminCodeRequest.Status.PENDING, AdminCodeRequest.Status.APPROVED))) {
            throw new IllegalArgumentException("Ya hay una solicitud activa para ese correo electrónico.");
        }

        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail(normalizedEmail);
        request.setNombres(normalizedNombres);
        request.setApellidos(normalizedApellidos);
        request.setMotivo(normalizedMotivo);
        request.setCreatedBy(requester.getUsername());
        request.setCreatedByName(requester.getNombres().trim() + " " + requester.getApellidos().trim());
        request = requestRepository.saveAndFlush(request);
        String plainNotification = "Nueva solicitud de acceso ADMIN\n\n"
                + "Persona recomendada: " + normalizedNombres + " " + normalizedApellidos + "\n"
                + "Correo del candidato: " + normalizedEmail + "\n"
                + "Persona que recomienda: " + request.getCreatedByName()
                + " (@" + requester.getUsername() + ")\n"
                + "Motivo de acceso: " + normalizedMotivo + "\n\n"
                + "Revisa la información y elige Aprobar o Rechazar desde los controles protegidos del panel:\n"
                + publicBaseUrl + "/admin/solicitudes-codigo#solicitud-" + request.getId() + "\n\n"
                + "Solicitud #" + request.getId() + ". Abrir el enlace no aprueba la solicitud.";
        String notificationHtml = buildEmailHtml(
            "Nueva solicitud administrativa",
            "Se recibió una recomendación que requiere revisión.",
            "Persona recomendada",
            escapeHtml(normalizedNombres + " " + normalizedApellidos),
            "Correo del candidato",
            escapeHtml(normalizedEmail),
            "Recomendada por",
            escapeHtml(request.getCreatedByName() + " (@"+ requester.getUsername() + ")"),
            "Motivo de acceso",
            escapeHtml(normalizedMotivo),
            publicBaseUrl + "/admin/solicitudes-codigo#solicitud-" + request.getId(),
            "Revisar solicitud",
            "Aprobar o Rechazar: esta acción solo puede realizarla el responsable configurado.");
        sendHtmlMail(approverEmail, "Nueva solicitud de acceso ADMIN", plainNotification, notificationHtml);
        return pushNotificationService.notifyPendingRequest(
            request.getId(), normalizedNombres, normalizedApellidos, normalizedEmail);
    }

    @Transactional(readOnly = true)
    public List<AdminCodeRequest> findAll() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public String approve(long requestId, String decidedBy) {
        AdminCodeRequest request = findPendingRequest(requestId);
        String accessCode = adminCodeService.issueAdminAccessCode(request.getEmail(), request.getId());
        request.setStatus(AdminCodeRequest.Status.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        requestRepository.save(request);
        return accessCode;
    }

    @Transactional
    public String replaceApprovedCode(long requestId) {
        AdminCodeRequest request = findByIdForUpdate(requestId);
        if (request.getStatus() != AdminCodeRequest.Status.APPROVED) {
            throw new IllegalArgumentException("Solo se puede generar un código nuevo para una solicitud aprobada.");
        }

        String accessCode = adminCodeService.replaceAdminAccessCode(request.getEmail(), requestId);
        request.setCodeEmailSentAt(null);
        request.setCodeEmailSentBy(null);
        requestRepository.save(request);
        return accessCode;
    }

    @Transactional
    public void sendApprovedCode(long requestId, String codigoAdmin, String sentBy) {
        AdminCodeRequest request = findByIdForUpdate(requestId);
        if (request.getStatus() != AdminCodeRequest.Status.APPROVED) {
            throw new IllegalArgumentException("La solicitud debe estar aprobada antes de enviar el código.");
        }

        AdminCode code = adminCodeService.findByCodigo(codigoAdmin);
        if (code == null || !Long.valueOf(requestId).equals(code.getRegistrationRequestId())
                || !request.getEmail().equalsIgnoreCase(code.getEmail())) {
            throw new IllegalArgumentException("El código no corresponde a esta solicitud aprobada.");
        }

        validateMailConfiguration();
        String plainCodeEmail = "Hola " + request.getNombres() + " " + request.getApellidos() + ".\n\n"
                + "Tu solicitud fue aprobada. Este es tu código personal de acceso administrativo:\n\n"
                + codigoAdmin.trim() + "\n\n"
                + "Abre " + publicBaseUrl + "/admin-login, ingresa el código y "
                + "confirma tus credenciales. El código también se utilizará para tus futuros accesos ADMIN.\n"
                + "No lo compartas. Si no esperabas este mensaje, contacta al administrador.";
        String codeHtml = buildCodeEmailHtml(request, codigoAdmin.trim());
        sendHtmlMail(request.getEmail(), "Tu código de acceso ADMIN de Bustraker", plainCodeEmail, codeHtml);
        request.setCodeEmailSentAt(LocalDateTime.now());
        request.setCodeEmailSentBy(sentBy);
        requestRepository.save(request);
    }

    @Transactional
    public void reject(long requestId, String decidedBy) {
        AdminCodeRequest request = findPendingRequest(requestId);
        validateMailConfiguration();
        String plainRejection = "Hola " + request.getNombres() + " " + request.getApellidos() + ".\n\n"
                + "Tu solicitud de acceso ADMIN no fue aprobada. No se generó ni se envió ningún código.";
        String rejectionHtml = buildEmailHtml(
            "Actualización de tu solicitud",
            "La solicitud de acceso administrativo no fue aprobada.",
            "Estado",
            "Solicitud rechazada",
            "Resultado",
            "No se generó ni se envió ningún código.",
            "Recomendación",
            escapeHtml(request.getNombres() + " " + request.getApellidos()),
            "Correo",
            escapeHtml(request.getEmail()),
            publicBaseUrl,
            "Volver a Bustraker",
            "Si tienes dudas, contacta al responsable del sistema.");
        sendHtmlMail(request.getEmail(), "Actualización de solicitud ADMIN de Bustraker",
            plainRejection, rejectionHtml);
        request.setStatus(AdminCodeRequest.Status.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        requestRepository.save(request);
    }

    @Transactional
    public Usuario completeRegistration(long requestId, long adminCodeId, String username, String password) {
        AdminCodeRequest request = requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("La solicitud de registro no existe."));
        if (request.getStatus() != AdminCodeRequest.Status.APPROVED) {
            throw new IllegalArgumentException("La solicitud ya fue completada o no está aprobada.");
        }

        var accessCode = adminCodeService.findById(adminCodeId)
            .orElseThrow(() -> new IllegalArgumentException("El código de acceso no es válido."));
        if (!Long.valueOf(requestId).equals(accessCode.getRegistrationRequestId())
                || !request.getEmail().equalsIgnoreCase(accessCode.getEmail())) {
            throw new IllegalArgumentException("El código no corresponde a esta solicitud.");
        }

        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!normalizedUsername.matches("^[a-z0-9._-]{4,20}$")) {
            throw new IllegalArgumentException("El nombre de usuario debe tener entre 4 y 20 caracteres válidos.");
        }
        if (usuarioRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Ese nombre de usuario ya está en uso.");
        }
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo electrónico.");
        }
        PasswordPolicy.validateNewPassword(password);

        Usuario usuario = new Usuario();
        usuario.setUsername(normalizedUsername);
        usuario.setPassword(password);
        usuario.setNombres(request.getNombres());
        usuario.setApellidos(request.getApellidos());
        usuario.setEmail(request.getEmail());
        usuario.setProvider("LOCAL");
        usuario.setRole("ROLE_ADMIN");
        Usuario registered = usuarioService.register(usuario);

        request.setStatus(AdminCodeRequest.Status.REGISTERED);
        requestRepository.save(request);
        return registered;
    }

    @Transactional(readOnly = true)
    public AdminCodeRequest findById(long requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("La solicitud de registro no existe."));
    }

    private AdminCodeRequest findPendingRequest(long requestId) {
        AdminCodeRequest request = findByIdForUpdate(requestId);
        if (request.getStatus() != AdminCodeRequest.Status.PENDING) {
            throw new IllegalArgumentException("La solicitud ya fue resuelta.");
        }
        return request;
    }

    private AdminCodeRequest findByIdForUpdate(long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
    }

    private Usuario findRequester(String createdBy) {
        String username = createdBy == null ? "" : createdBy.trim();
        if (username.isEmpty()) {
            throw new IllegalArgumentException("No se pudo identificar al administrador que recomienda.");
        }
        Usuario requester = usuarioRepository.findByUsername(username);
        if (requester == null || requester.getNombres() == null || requester.getNombres().isBlank()
                || requester.getApellidos() == null || requester.getApellidos().isBlank()) {
            throw new IllegalStateException("No se pudo obtener el nombre del administrador que recomienda.");
        }
        return requester;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > 254
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        }
        return normalizedEmail.toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > 100
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Ingresa un " + fieldName + " válido.");
        }
        return normalized;
    }

    private String normalizeMotivo(String motivo) {
        String normalized = motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 20 || normalized.length() > 500
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Describe el motivo de acceso con entre 20 y 500 caracteres.");
        }
        return normalized;
    }

    private void validateMailConfiguration() {
        if (approverEmail.isBlank() || senderEmail.isBlank() || senderPassword.isBlank()) {
            throw new IllegalStateException(
                "El flujo de invitaciones ADMIN no está configurado.");
        }
    }

    private void sendMail(String recipient, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("No se pudo enviar el correo de la solicitud ADMIN.", exception);
        }
    }

    private void sendHtmlMail(String recipient, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("No se pudo enviar el correo de la solicitud ADMIN.", exception);
        }
    }

    private String buildCodeEmailHtml(AdminCodeRequest request, String code) {
        String safeName = escapeHtml(request.getNombres() + " " + request.getApellidos());
        String safeCode = escapeHtml(code);
        return buildEmailHtml(
            "Solicitud aprobada",
            "Tu acceso administrativo está listo para activarse.",
            "Hola",
            safeName,
            "Estado",
            "Aprobado",
            "Código de acceso",
            safeCode,
            "Próximo paso",
            "Verifica el código y crea tus credenciales.",
            publicBaseUrl + "/admin-login",
            "Activar acceso ADMIN",
            "No compartas este código. Si no solicitaste el acceso, contacta al administrador.");
    }

    private String buildEmailHtml(String title, String intro, String label1, String value1,
                                  String label2, String value2, String label3, String value3,
                                  String label4, String value4, String actionUrl, String actionLabel,
                                  String footerText) {
        return "<!doctype html><html lang=\"es\"><body style=\"margin:0;background:#f4eff0;font-family:Segoe UI,Arial,sans-serif;color:#2e2326\">"
            + "<div style=\"max-width:620px;margin:0 auto;padding:32px 16px\">"
            + "<div style=\"overflow:hidden;border:1px solid #e8dcde;border-radius:18px;background:#fff;box-shadow:0 12px 35px rgba(54,29,34,.10)\">"
            + "<div style=\"padding:22px 28px;background:#24191c;color:#fff\"><strong style=\"font-size:18px\">Bustraker</strong><span style=\"float:right;color:#d7c9cc;font-size:11px;letter-spacing:.08em\">ACCESO ADMINISTRATIVO</span></div>"
            + "<div style=\"padding:34px 28px 28px\"><div style=\"display:inline-block;padding:6px 9px;border-radius:999px;background:#fff1f3;color:#963344;font-size:10px;font-weight:700;letter-spacing:.06em\">VERIFICACIÓN SEGURA</div>"
            + "<h1 style=\"margin:16px 0 8px;font-size:28px;line-height:1.15\">" + escapeHtml(title) + "</h1>"
            + "<p style=\"margin:0 0 24px;color:#786b6f;font-size:14px;line-height:1.6\">" + escapeHtml(intro) + "</p>"
            + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse\">"
            + emailRow(label1, value1) + emailRow(label2, value2) + emailRow(label3, value3) + emailRow(label4, value4)
            + "</table><p style=\"margin:24px 0 0\"><a href=\"" + escapeHtml(actionUrl) + "\" style=\"display:inline-block;padding:13px 18px;border-radius:9px;background:#963344;color:#fff;text-decoration:none;font-size:13px;font-weight:700\">"
            + escapeHtml(actionLabel) + "</a></p><p style=\"margin:20px 0 0;color:#918287;font-size:11px;line-height:1.55\">" + escapeHtml(footerText) + "</p>"
            + "</div></div><p style=\"margin:16px 0;text-align:center;color:#9a8c90;font-size:10px\">Bustraker · Sistema de gestión de transporte</p></div></body></html>";
    }

    private String emailRow(String label, String value) {
        return "<tr><td style=\"width:38%;padding:11px 12px;border-bottom:1px solid #eee6e7;color:#9a8c90;font-size:11px;font-weight:700\">"
            + escapeHtml(label) + "</td><td style=\"padding:11px 12px;border-bottom:1px solid #eee6e7;color:#3f3437;font-size:12px;line-height:1.5\">"
            + value + "</td></tr>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
