package com.proaula.aula.Service;

import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Repository.AdminCodeRequestRepository;
import com.proaula.aula.Repository.UsuarioRepository;
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

@Service
public class AdminCodeRequestService {

    private final AdminCodeRequestRepository requestRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdminCodeService adminCodeService;
    private final AdminPushNotificationService pushNotificationService;
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
            JavaMailSender mailSender,
            @Value("${app.admin.approver-email:}") String approverEmail,
            @Value("${spring.mail.username:}") String senderEmail,
            @Value("${spring.mail.password:}") String senderPassword,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.requestRepository = requestRepository;
        this.usuarioRepository = usuarioRepository;
        this.adminCodeService = adminCodeService;
        this.pushNotificationService = pushNotificationService;
        this.mailSender = mailSender;
        this.approverEmail = approverEmail;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void createRequest(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateMailConfiguration();
        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        if (requestRepository.existsByEmailIgnoreCaseAndStatus(normalizedEmail, AdminCodeRequest.Status.PENDING)) {
            return;
        }

        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail(normalizedEmail);
        request = requestRepository.saveAndFlush(request);
        sendMail(approverEmail, "Nueva solicitud de acceso ADMIN",
            "Se recibió una solicitud de código ADMIN para: " + normalizedEmail + ".\n\n"
                + "Revisar y decidir desde la sesión administrativa:\n"
                + publicBaseUrl + "/admin/solicitudes-codigo\n\n"
                + "Solicitud #" + request.getId() + ". No respondas a este mensaje para aprobarla.");
        pushNotificationService.notifyPendingRequest(request.getId());
    }

    @Transactional(readOnly = true)
    public List<AdminCodeRequest> findAll() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void approve(long requestId, String decidedBy) {
        AdminCodeRequest request = findPendingRequest(requestId);
        validateMailConfiguration();
        String inviteCode = adminCodeService.issueRegistrationInvite(request.getEmail());
        sendMail(request.getEmail(), "Tu código de invitación ADMIN de Bustraker",
            "Tu solicitud fue aprobada.\n\n"
                + "Código de invitación (válido durante 24 horas y para un solo registro):\n"
                + inviteCode + "\n\n"
                + "Úsalo al crear tu cuenta ADMIN en: " + publicBaseUrl + "/registro-mejorado\n"
                + "No compartas este código. Si no solicitaste acceso, ignora este mensaje.");
        request.setStatus(AdminCodeRequest.Status.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        requestRepository.save(request);
    }

    @Transactional
    public void reject(long requestId, String decidedBy) {
        AdminCodeRequest request = findPendingRequest(requestId);
        validateMailConfiguration();
        sendMail(request.getEmail(), "Actualización de solicitud ADMIN de Bustraker",
            "Tu solicitud de acceso ADMIN no fue aprobada. No se generó ni se envió ningún código.");
        request.setStatus(AdminCodeRequest.Status.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        requestRepository.save(request);
    }

    private AdminCodeRequest findPendingRequest(long requestId) {
        AdminCodeRequest request = requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
        if (request.getStatus() != AdminCodeRequest.Status.PENDING) {
            throw new IllegalArgumentException("La solicitud ya fue resuelta.");
        }
        return request;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > 254
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        }
        return normalizedEmail.toLowerCase(Locale.ROOT);
    }

    private void validateMailConfiguration() {
        if (approverEmail.isBlank() || senderEmail.isBlank() || senderPassword.isBlank()) {
            throw new IllegalStateException(
                "El flujo de invitaciones ADMIN no está configurado.");
        }
        if (!approverEmail.equalsIgnoreCase(senderEmail)) {
            throw new IllegalStateException(
                "El correo remitente debe coincidir con la cuenta designada para aprobar solicitudes ADMIN.");
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
}
