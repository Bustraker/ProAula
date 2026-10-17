package com.proaula.aula.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpSession;

import com.proaula.aula.Entity.AdminInviteCode;
import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Repository.AdminCodeRepository;
import com.proaula.aula.Repository.AdminInviteCodeRepository;
import com.proaula.aula.Service.AdminCodeService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
@Transactional
class AdminCodeApprovalFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminCodeService adminCodeService;

    @Autowired
    private AdminInviteCodeRepository inviteCodeRepository;

    @Autowired
    private AdminCodeRepository adminCodeRepository;

    @Test
    void adminLoginRecognizesOnlyCodesPersistedInTheDatabase() throws Exception {
        String savedCode = "db-admin-code";
        adminCodeService.ensureDefaultAdminCodes(java.util.List.of(savedCode));

        AdminCode storedCode = adminCodeRepository.findAll().get(0);
        assertThat(storedCode.getCodeHash())
            .matches("sha256\\$[0-9a-f]{64}")
            .isNotEqualTo(savedCode);

        MvcResult accepted = mockMvc.perform(post("/admin/verificar-codigo")
                .with(csrf())
                .param("codigoAdmin", savedCode.toUpperCase()))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/admin-login?verified=true"))
            .andReturn();
        HttpSession acceptedSession = accepted.getRequest().getSession(false);
        assertThat(acceptedSession).isNotNull();
        assertThat(acceptedSession.getAttribute("adminCodeVerified")).isEqualTo(true);

        MvcResult rejected = mockMvc.perform(post("/admin/verificar-codigo")
                .with(csrf())
                .param("codigoAdmin", "not-in-database"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/admin-login"))
            .andReturn();
        HttpSession rejectedSession = rejected.getRequest().getSession(false);
        assertThat(rejectedSession == null || rejectedSession.getAttribute("adminCodeVerified") == null).isTrue();
    }

    @Test
    void legacyAdminCodeIsHashedAndCheckedAgainstTheDatabase() {
        adminCodeRepository.saveAndFlush(new AdminCode("legacy-admin-code"));

        adminCodeService.migrateLegacyAdminCodes();

        AdminCode storedCode = adminCodeRepository.findAll().get(0);
        assertThat(storedCode.getCodeHash())
            .matches("sha256\\$[0-9a-f]{64}")
            .isNotEqualTo("legacy-admin-code");
        assertThat(adminCodeService.isValidAdminCode("LEGACY-ADMIN-CODE")).isTrue();
        assertThat(adminCodeService.isValidAdminCode("unknown-code")).isFalse();
    }

    @Test
    void publicRequestFormHandlesMissingMalformedAndUnconfiguredSubmissionsSafely() throws Exception {
        MvcResult health = mockMvc.perform(get("/actuator/health"))
            .andExpect(jsonPath("$.status").exists())
            .andReturn();
        assertThat(health.getResponse().getStatus()).isIn(200, 503);

        mockMvc.perform(get("/solicitar-codigo-admin"))
            .andExpect(status().isOk());

        MvcResult missingEmail = mockMvc.perform(post("/solicitar-codigo-admin").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/solicitar-codigo-admin"))
            .andReturn();
        assertThat(missingEmail.getFlashMap().get("error").toString())
            .contains("correo electrónico válido");

        MvcResult malformedEmail = mockMvc.perform(post("/solicitar-codigo-admin")
                .with(csrf())
                .param("email", "not-an-email"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        assertThat(malformedEmail.getFlashMap().get("error").toString())
            .contains("correo electrónico válido");

        MvcResult unconfiguredMail = mockMvc.perform(post("/solicitar-codigo-admin")
                .with(csrf())
                .param("email", "applicant@example.test"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        assertThat(unconfiguredMail.getFlashMap().get("error").toString())
            .contains("Inténtalo más tarde")
            .doesNotContain("MAIL_USERNAME", "ADMIN_APPROVER_EMAIL");
    }

    @Test
    void adminInboxAndDecisionActionsRejectAnonymousAndNonAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/solicitudes-codigo"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/solicitudes-codigo/1/aprobar").with(csrf()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/solicitudes-codigo/1/aprobar")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ordinary-user")
                    .roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "not-the-approver", roles = "ADMIN")
    void adminRoleAloneCannotAccessApprovalInboxOrProcessRequests() throws Exception {
        mockMvc.perform(get("/admin/solicitudes-codigo"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/solicitudes-codigo/1/aprobar").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void registrationInviteIsHashedEmailBoundExpiringAndSingleUse() {
        String email = "invite-flow@example.test";
        String code = adminCodeService.issueRegistrationInvite(email);
        AdminInviteCode storedInvite = inviteCodeRepository.findAll().stream()
            .filter(invite -> invite.getEmail().equals(email))
            .findFirst()
            .orElseThrow();

        assertThat(storedInvite.getCodeHash()).hasSize(64).isNotEqualTo(code);
        assertThat(adminCodeService.consumeRegistrationInvite(code, "different@example.test")).isFalse();
        assertThat(adminCodeService.consumeRegistrationInvite(code, email.toUpperCase())).isTrue();
        assertThat(adminCodeService.consumeRegistrationInvite(code, email)).isFalse();

        String expiredCode = adminCodeService.issueRegistrationInvite("expired-flow@example.test");
        AdminInviteCode expiredInvite = inviteCodeRepository.findAll().stream()
            .filter(invite -> invite.getEmail().equals("expired-flow@example.test"))
            .findFirst()
            .orElseThrow();
        expiredInvite.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        inviteCodeRepository.saveAndFlush(expiredInvite);

        assertThat(adminCodeService.consumeRegistrationInvite(expiredCode, "expired-flow@example.test")).isFalse();
    }

    @Test
    void adminDecisionPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/admin/solicitudes-codigo/1/aprobar")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                    "not-the-approver").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
