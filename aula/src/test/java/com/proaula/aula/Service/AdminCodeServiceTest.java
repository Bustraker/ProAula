package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Entity.AdminInviteCode;
import com.proaula.aula.Repository.AdminCodeRepository;
import com.proaula.aula.Repository.AdminInviteCodeRepository;

@ExtendWith(MockitoExtension.class)
class AdminCodeServiceTest {

    @Mock
    private AdminCodeRepository adminCodeRepository;

    @Mock
    private AdminInviteCodeRepository inviteCodeRepository;

    @InjectMocks
    private AdminCodeService adminCodeService;

    @Test
    void defaultAdminCodesAreHashedAndValidatedThroughTheDatabase() {
        adminCodeService.ensureDefaultAdminCodes(List.of("  my-admin-code "));

        ArgumentCaptor<AdminCode> codeCaptor = ArgumentCaptor.forClass(AdminCode.class);
        verify(adminCodeRepository).save(codeCaptor.capture());
        AdminCode savedCode = codeCaptor.getValue();
        assertThat(savedCode.getCodeHash())
            .matches("sha256\\$[0-9a-f]{64}")
            .isNotEqualTo("my-admin-code");

        clearInvocations(adminCodeRepository);
        when(adminCodeRepository.existsByCodeHash(savedCode.getCodeHash())).thenReturn(true);
        assertThat(adminCodeService.isValidAdminCode("MY-ADMIN-CODE")).isTrue();
        verify(adminCodeRepository).existsByCodeHash(savedCode.getCodeHash());
        assertThat(adminCodeService.isValidAdminCode(null)).isFalse();
        verify(adminCodeRepository, never()).existsByCodeHash(null);
    }

    @Test
    void migratesLegacyAdminCodesBeforeTheyAreUsed() {
        AdminCode legacyCode = new AdminCode("legacy-admin-code");
        when(adminCodeRepository.findAll()).thenReturn(List.of(legacyCode));

        adminCodeService.migrateLegacyAdminCodes();

        assertThat(legacyCode.getCodeHash())
            .matches("sha256\\$[0-9a-f]{64}")
            .isNotEqualTo("legacy-admin-code");
        verify(adminCodeRepository).saveAll(List.of(legacyCode));
    }

    @Test
    void issueRegistrationInviteStoresOnlyHashAndExpiresAfter24Hours() {
        LocalDateTime beforeIssue = LocalDateTime.now();

        String code = adminCodeService.issueRegistrationInvite(" Applicant@Example.com ");

        ArgumentCaptor<AdminInviteCode> inviteCaptor = ArgumentCaptor.forClass(AdminInviteCode.class);
        verify(inviteCodeRepository).save(inviteCaptor.capture());
        AdminInviteCode invite = inviteCaptor.getValue();

        assertThat(code).hasSize(24).matches("[A-HJ-NP-Z2-9]+");
        assertThat(invite.getEmail()).isEqualTo("applicant@example.com");
        assertThat(invite.getCodeHash()).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(code);
        assertThat(invite.getExpiresAt())
            .isAfterOrEqualTo(beforeIssue.plusHours(24))
            .isBeforeOrEqualTo(LocalDateTime.now().plusHours(24));
    }

    @Test
    void consumeRegistrationInviteMarksMatchingInviteUsed() {
        String code = adminCodeService.issueRegistrationInvite("applicant@example.com");
        ArgumentCaptor<AdminInviteCode> inviteCaptor = ArgumentCaptor.forClass(AdminInviteCode.class);
        verify(inviteCodeRepository).save(inviteCaptor.capture());
        AdminInviteCode invite = inviteCaptor.getValue();
        when(inviteCodeRepository.findByCodeHashAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                eq(invite.getCodeHash()), eq("APPLICANT@example.com"), any(LocalDateTime.class)))
            .thenReturn(Optional.of(invite));

        assertThat(adminCodeService.consumeRegistrationInvite(code.toLowerCase(Locale.ROOT), "APPLICANT@example.com"))
            .isTrue();
        assertThat(invite.getUsedAt()).isNotNull();
        verify(inviteCodeRepository).saveAndFlush(invite);
    }

    @Test
    void consumeRegistrationInviteRejectsInvalidInputsAndUnmatchedCodes() {
        assertThat(adminCodeService.consumeRegistrationInvite(null, "applicant@example.com")).isFalse();
        assertThat(adminCodeService.consumeRegistrationInvite("  ", "applicant@example.com")).isFalse();
        assertThat(adminCodeService.consumeRegistrationInvite("valid-looking", " ")).isFalse();
        when(inviteCodeRepository.findByCodeHashAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                any(String.class), eq("other@example.com"), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        assertThat(adminCodeService.consumeRegistrationInvite("valid-looking", "other@example.com")).isFalse();
    }
}
