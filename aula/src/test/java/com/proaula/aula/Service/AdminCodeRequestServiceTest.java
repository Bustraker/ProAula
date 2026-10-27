package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.AdminCodeRequestRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class AdminCodeRequestServiceTest {

    @Mock
    private AdminCodeRequestRepository requestRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AdminCodeService adminCodeService;

    @Mock
    private AdminPushNotificationService pushNotificationService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private JavaMailSender mailSender;

    private AdminCodeRequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new AdminCodeRequestService(
            requestRepository,
            usuarioRepository,
            adminCodeService,
            pushNotificationService,
            usuarioService,
            mailSender,
            "approver@example.com",
            "notifications@example.com",
            "test-only-mail-password",
            "https://bustraker.example");
    }

    @Test
    void createRequestNormalizesApplicantDataAndNotifiesApprover() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(usuarioRepository.existsByEmail("applicant@example.com")).thenReturn(false);
        when(usuarioRepository.findByUsername("approver")).thenReturn(adminRequester());
        when(requestRepository.existsByEmailIgnoreCaseAndStatusIn(
                eq("applicant@example.com"), any()))
            .thenReturn(false);
        when(requestRepository.saveAndFlush(any(AdminCodeRequest.class)))
            .thenAnswer(invocation -> {
                AdminCodeRequest request = invocation.getArgument(0);
                ReflectionTestUtils.setField(request, "id", 1L);
                return request;
            });
        when(pushNotificationService.notifyPendingRequest(
                1L, "Andrea", "Perez", "applicant@example.com"))
            .thenReturn(true);

        assertThat(requestService.createRequest(
            " Andrea ", " Perez ", " Applicant@Example.com ",
            " Necesita administrar las rutas y revisar la operación diaria. ", "approver")).isTrue();

        org.mockito.ArgumentCaptor<AdminCodeRequest> requestCaptor =
            org.mockito.ArgumentCaptor.forClass(AdminCodeRequest.class);
        verify(requestRepository).saveAndFlush(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getNombres()).isEqualTo("Andrea");
        assertThat(requestCaptor.getValue().getApellidos()).isEqualTo("Perez");
        assertThat(requestCaptor.getValue().getEmail()).isEqualTo("applicant@example.com");
        assertThat(requestCaptor.getValue().getCreatedBy()).isEqualTo("approver");
        assertThat(requestCaptor.getValue().getCreatedByName()).isEqualTo("Admin Approver");
        assertThat(requestCaptor.getValue().getMotivo())
            .isEqualTo("Necesita administrar las rutas y revisar la operación diaria.");
        org.mockito.ArgumentCaptor<MimeMessage> messageCaptor =
            org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(messageCaptor.capture());
        MimeMessage notification = messageCaptor.getValue();
        assertThat(notification.getFrom()[0].toString()).isEqualTo("notifications@example.com");
        assertThat(notification.getRecipients(Message.RecipientType.TO)[0].toString())
            .isEqualTo("approver@example.com");
        assertThat(notification.getContent().toString())
            .contains("Andrea Perez", "applicant@example.com", "Admin Approver", "Necesita administrar las rutas",
                "/admin/solicitudes-codigo#solicitud-1", "Aprobar o Rechazar")
            .doesNotContain("password", "contraseña");
        verify(pushNotificationService).notifyPendingRequest(
            1L, "Andrea", "Perez", "applicant@example.com");
    }

    @Test
    void approveIssuesAccessCodeAndPersistsDecision() {
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        request.setNombres("Andrea");
        request.setApellidos("Perez");
        ReflectionTestUtils.setField(request, "id", 7L);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(adminCodeService.issueAdminAccessCode("applicant@example.com", 7L)).thenReturn("Nube-Ruta-Luna-4827!#");

        String issuedCode = requestService.approve(7L, "approver");

        assertThat(issuedCode).isEqualTo("Nube-Ruta-Luna-4827!#");
        assertThat(request.getStatus()).isEqualTo(AdminCodeRequest.Status.APPROVED);
        assertThat(request.getDecidedAt()).isNotNull();
        assertThat(request.getDecidedBy()).isEqualTo("approver");
        verify(adminCodeService).issueAdminAccessCode("applicant@example.com", 7L);
        verifyNoInteractions(mailSender);
        verify(requestRepository).save(request);
    }

    @Test
    void codeIsEmailedOnlyAfterExplicitRequestAndDeliveryIsRecorded() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        request.setNombres("Andrea");
        request.setApellidos("Perez");
        request.setStatus(AdminCodeRequest.Status.APPROVED);
        ReflectionTestUtils.setField(request, "id", 15L);
        AdminCode accessCode = new AdminCode("sha256$" + "a".repeat(64));
        accessCode.setEmail("applicant@example.com");
        accessCode.setRegistrationRequestId(15L);
        when(requestRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(request));
        when(adminCodeService.findByCodigo("Nube-Ruta-Luna-4827!#")).thenReturn(accessCode);

        requestService.sendApprovedCode(15L, "Nube-Ruta-Luna-4827!#", "approver");

        assertThat(request.getCodeEmailSentAt()).isNotNull();
        assertThat(request.getCodeEmailSentBy()).isEqualTo("approver");
        org.mockito.ArgumentCaptor<MimeMessage> messageCaptor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRecipients(Message.RecipientType.TO)[0].toString())
            .isEqualTo("applicant@example.com");
        assertThat(messageCaptor.getValue().getContent().toString())
            .contains("Nube-Ruta-Luna-4827!#", "/admin-login");
        verify(requestRepository).save(request);
    }

    @Test
    void rejectDoesNotIssueInviteAndPersistsRejection() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        when(requestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(request));

        requestService.reject(8L, "approver");

        assertThat(request.getStatus()).isEqualTo(AdminCodeRequest.Status.REJECTED);
        assertThat(request.getDecidedAt()).isNotNull();
        assertThat(request.getDecidedBy()).isEqualTo("approver");
        verify(adminCodeService, never()).issueAdminAccessCode(any(String.class), org.mockito.ArgumentMatchers.anyLong());
        verify(mailSender).send(any(MimeMessage.class));
        verify(requestRepository).save(request);
    }

    @Test
    void createRequestRejectsMalformedOrMissingDataWithoutSideEffects() {
        assertThatThrownBy(() -> requestService.createRequest(
                null, "Perez", "applicant@example.com", "Motivo suficientemente largo", "admin"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.createRequest(
                "Andrea", "Perez", "not-an-email", "Motivo suficientemente largo", "admin"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.createRequest(
                "Andrea", "Perez", "x".repeat(250) + "@example.com",
                "Motivo suficientemente largo", "admin"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(requestRepository, usuarioRepository, mailSender, pushNotificationService, usuarioService);
    }

    @Test
    void createRequestRejectsMissingOrShortAccessReason() {
        assertThatThrownBy(() -> requestService.createRequest(
                "Andrea", "Perez", "applicant@example.com", "Acceso", "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("20 y 500");
        verifyNoInteractions(requestRepository, usuarioRepository, mailSender, pushNotificationService, usuarioService);
    }

    @Test
    void createRequestRejectsExistingAccountsAndActiveRequests() {
        when(usuarioRepository.existsByEmail("exists@example.com")).thenReturn(true);
        when(usuarioRepository.existsByEmail("pending@example.com")).thenReturn(false);
        when(requestRepository.existsByEmailIgnoreCaseAndStatusIn(
                "pending@example.com", java.util.List.of(
                    AdminCodeRequest.Status.PENDING, AdminCodeRequest.Status.APPROVED)))
            .thenReturn(true);

        when(usuarioRepository.findByUsername("admin")).thenReturn(adminRequester());
        assertThatThrownBy(() -> requestService.createRequest(
                "A", "B", "exists@example.com", "Necesita administrar rutas de la plataforma.", "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> requestService.createRequest(
                "A", "B", "pending@example.com", "Necesita administrar rutas de la plataforma.", "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("solicitud activa");

        verify(requestRepository, never()).saveAndFlush(any(AdminCodeRequest.class));
        verifyNoInteractions(mailSender, pushNotificationService);
    }

    @Test
    void createRequestFailsClosedWhenMailConfigurationIsMissing() {
        AdminCodeRequestService missingPasswordService = new AdminCodeRequestService(
            requestRepository, usuarioRepository, adminCodeService, pushNotificationService, usuarioService, mailSender,
            "approver@example.com", "approver@example.com", "", "https://bustraker.example");

        assertThatThrownBy(() -> missingPasswordService.createRequest(
                "A", "B", "applicant@example.com", "Necesita administrar rutas de la plataforma.", "admin"))
            .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(usuarioRepository, requestRepository, mailSender, pushNotificationService, usuarioService);
    }

    @Test
    void codeIssuanceFailureDoesNotMarkRequestApproved() {
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        request.setNombres("Andrea");
        request.setApellidos("Perez");
        ReflectionTestUtils.setField(request, "id", 9L);
        when(requestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        when(adminCodeService.issueAdminAccessCode("applicant@example.com", 9L))
            .thenThrow(new IllegalStateException("code storage failed"));

        assertThatThrownBy(() -> requestService.approve(9L, "approver"))
            .isInstanceOf(IllegalStateException.class);

        assertThat(request.getStatus()).isEqualTo(AdminCodeRequest.Status.PENDING);
        assertThat(request.getDecidedAt()).isNull();
        verify(requestRepository, never()).save(request);
    }

    @Test
    void alreadyResolvedOrMissingRequestCannotBeApprovedOrRejected() {
        AdminCodeRequest resolved = new AdminCodeRequest();
        resolved.setStatus(AdminCodeRequest.Status.REJECTED);
        when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(resolved));
        when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.approve(10L, "approver"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.reject(10L, "approver"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.approve(11L, "approver"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(adminCodeService, mailSender);
        verify(requestRepository, never()).save(any(AdminCodeRequest.class));
    }

    @Test
    void completeRegistrationRejectsPasswordsThatExceedBcryptUtf8Limit() {
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        request.setStatus(AdminCodeRequest.Status.APPROVED);
        ReflectionTestUtils.setField(request, "id", 12L);
        when(requestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));

        AdminCode accessCode = new AdminCode("sha256$" + "a".repeat(64));
        accessCode.setEmail("applicant@example.com");
        accessCode.setRegistrationRequestId(12L);
        when(adminCodeService.findById(34L)).thenReturn(Optional.of(accessCode));

        assertThatThrownBy(() -> requestService.completeRegistration(
                12L, 34L, "applicantadmin", "ñ".repeat(40)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("72 bytes");

        verify(usuarioService, never()).register(any());
    }

    private Usuario adminRequester() {
        Usuario requester = new Usuario();
        requester.setUsername("approver");
        requester.setNombres("Admin");
        requester.setApellidos("Approver");
        requester.setEmail("approver@example.com");
        return requester;
    }
}
