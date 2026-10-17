package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Session;
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
import com.proaula.aula.Repository.AdminCodeRequestRepository;
import com.proaula.aula.Repository.UsuarioRepository;

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
    private JavaMailSender mailSender;

    private AdminCodeRequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new AdminCodeRequestService(
            requestRepository,
            usuarioRepository,
            adminCodeService,
            pushNotificationService,
            mailSender,
            "approver@example.com",
            "approver@example.com",
            "test-only-mail-password",
            "https://bustraker.example");
    }

    @Test
    void createRequestNormalizesEmailAndNotifiesApprover() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(usuarioRepository.existsByEmail("applicant@example.com")).thenReturn(false);
        when(requestRepository.existsByEmailIgnoreCaseAndStatus(
                "applicant@example.com", AdminCodeRequest.Status.PENDING))
            .thenReturn(false);
        when(requestRepository.saveAndFlush(any(AdminCodeRequest.class)))
            .thenAnswer(invocation -> {
                AdminCodeRequest request = invocation.getArgument(0);
                ReflectionTestUtils.setField(request, "id", 1L);
                return request;
            });

        requestService.createRequest(" Applicant@Example.com ");

        verify(requestRepository).saveAndFlush(any(AdminCodeRequest.class));
        verify(mailSender).send(any(MimeMessage.class));
        verify(pushNotificationService).notifyPendingRequest(1L);
    }

    @Test
    void approveSendsInviteAndPersistsDecision() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(adminCodeService.issueRegistrationInvite("applicant@example.com")).thenReturn("INVITE-CODE");

        requestService.approve(7L, "approver");

        assertThat(request.getStatus()).isEqualTo(AdminCodeRequest.Status.APPROVED);
        assertThat(request.getDecidedAt()).isNotNull();
        assertThat(request.getDecidedBy()).isEqualTo("approver");
        verify(adminCodeService).issueRegistrationInvite("applicant@example.com");
        verify(mailSender).send(any(MimeMessage.class));
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
        verify(adminCodeService, never()).issueRegistrationInvite(any(String.class));
        verify(mailSender).send(any(MimeMessage.class));
        verify(requestRepository).save(request);
    }

    @Test
    void createRequestRejectsMalformedOrMissingEmailWithoutSideEffects() {
        assertThatThrownBy(() -> requestService.createRequest(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.createRequest("not-an-email"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestService.createRequest("x".repeat(250) + "@example.com"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(requestRepository, usuarioRepository, mailSender, pushNotificationService);
    }

    @Test
    void createRequestDoesNotRevealExistingAccountOrPendingRequestAndSendsNothing() {
        when(usuarioRepository.existsByEmail("exists@example.com")).thenReturn(true);
        when(usuarioRepository.existsByEmail("pending@example.com")).thenReturn(false);
        when(requestRepository.existsByEmailIgnoreCaseAndStatus(
                "pending@example.com", AdminCodeRequest.Status.PENDING))
            .thenReturn(true);

        requestService.createRequest("exists@example.com");
        requestService.createRequest("pending@example.com");

        verify(requestRepository, never()).saveAndFlush(any(AdminCodeRequest.class));
        verifyNoInteractions(mailSender, pushNotificationService);
    }

    @Test
    void createRequestFailsClosedWhenMailConfigurationIsMissingOrSenderDiffers() {
        AdminCodeRequestService missingPasswordService = new AdminCodeRequestService(
            requestRepository, usuarioRepository, adminCodeService, pushNotificationService, mailSender,
            "approver@example.com", "approver@example.com", "", "https://bustraker.example");
        AdminCodeRequestService mismatchedSenderService = new AdminCodeRequestService(
            requestRepository, usuarioRepository, adminCodeService, pushNotificationService, mailSender,
            "approver@example.com", "another@example.com", "test-only-mail-password", "https://bustraker.example");

        assertThatThrownBy(() -> missingPasswordService.createRequest("applicant@example.com"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mismatchedSenderService.createRequest("applicant@example.com"))
            .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(usuarioRepository, requestRepository, mailSender, pushNotificationService);
    }

    @Test
    void approvalFailureDoesNotMarkRequestApproved() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        AdminCodeRequest request = new AdminCodeRequest();
        request.setEmail("applicant@example.com");
        when(requestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        when(adminCodeService.issueRegistrationInvite("applicant@example.com")).thenReturn("INVITE-CODE");
        doThrow(new MailSendException("mail transport failed")).when(mailSender).send(any(MimeMessage.class));

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
}
