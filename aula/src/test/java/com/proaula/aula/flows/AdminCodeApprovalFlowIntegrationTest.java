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
import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.AdminCodeRequestRepository;
import com.proaula.aula.Repository.AdminCodeRepository;
import com.proaula.aula.Repository.AdminInviteCodeRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.AdminCodeService;
import com.proaula.aula.Service.UsuarioService;

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
    private UsuarioService usuarioService;

    @Autowired
    private AdminInviteCodeRepository inviteCodeRepository;

    @Autowired
    private AdminCodeRepository adminCodeRepository;

    @Autowired
    private AdminCodeRequestRepository requestRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

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
    void legacyPublicAdminRequestPageIsNoLongerAvailable() throws Exception {
        MvcResult health = mockMvc.perform(get("/actuator/health"))
            .andExpect(jsonPath("$.status").exists())
            .andReturn();
        assertThat(health.getResponse().getStatus()).isIn(200, 503);

        mockMvc.perform(get("/solicitar-codigo-admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "http://localhost/inicio-de-sesion-mejorado"));
    }

    @Test
    void approvedCandidateCanCompleteRegistrationAndKeepTheirEmailBoundAccessCode() throws Exception {
        AdminCodeRequest request = new AdminCodeRequest();
        request.setNombres("Candidata");
        request.setApellidos("Autorizada");
        request.setEmail("approved-candidate@example.test");
        request.setCreatedBy("existing-admin");
        request.setStatus(AdminCodeRequest.Status.APPROVED);
        request.setDecidedBy("approver");
        request.setDecidedAt(LocalDateTime.now());
        request = requestRepository.saveAndFlush(request);
        String accessCode = adminCodeService.issueAdminAccessCode(request.getEmail(), request.getId());

        MvcResult verification = mockMvc.perform(post("/admin/verificar-codigo")
                .with(csrf())
                .param("codigoAdmin", accessCode))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/admin-login?verified=true"))
            .andReturn();
        HttpSession session = verification.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/admin-login").session((org.springframework.mock.web.MockHttpSession) session))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                .attribute("registrationPending", true));

        mockMvc.perform(post("/admin/registrar")
                .with(csrf())
                .session((org.springframework.mock.web.MockHttpSession) session)
                .param("username", "approvedadmin")
                .param("password", "candidate-password-26")
                .param("passwordConfirm", "candidate-password-26"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/admin-login?verified=true"));

        var registered = usuarioRepository.findByUsername("approvedadmin");
        assertThat(registered).isNotNull();
        assertThat(registered.getEmail()).isEqualTo("approved-candidate@example.test");
        assertThat(registered.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(passwordEncoder.matches("candidate-password-26", registered.getPassword())).isTrue();
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
            .isEqualTo(AdminCodeRequest.Status.REGISTERED);
        assertThat(adminCodeService.isValidAdminCode(accessCode)).isTrue();

        MvcResult returningCode = mockMvc.perform(post("/admin/verificar-codigo")
                .with(csrf())
                .param("codigoAdmin", accessCode))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        HttpSession loginSession = returningCode.getRequest().getSession(false);

        Usuario otherAdmin = new Usuario();
        otherAdmin.setUsername("otheradmin");
        otherAdmin.setEmail("other-admin@example.test");
        otherAdmin.setPassword("candidate-password-26");
        otherAdmin.setNombres("Otro");
        otherAdmin.setApellidos("Administrador");
        otherAdmin.setRole("ROLE_ADMIN");
        otherAdmin.setProvider("LOCAL");
        usuarioService.register(otherAdmin);

        mockMvc.perform(post("/admin/login")
                .with(csrf())
                .session((org.springframework.mock.web.MockHttpSession) loginSession)
                .param("username", "otheradmin")
                .param("password", "candidate-password-26"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/admin-login?verified=true"));

        mockMvc.perform(post("/admin/login")
                .with(csrf())
                .session((org.springframework.mock.web.MockHttpSession) loginSession)
                .param("username", "approvedadmin")
                .param("password", "candidate-password-26"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/index_2"));
    }

    @Test
    void onlyAuthenticatedAdministratorsCanSubmitRegistrationRequests() throws Exception {
        mockMvc.perform(post("/admin/solicitudes-codigo")
                .with(csrf())
                .param("nombres", "Candidato")
                .param("apellidos", "Prueba")
                .param("email", "candidate@example.test"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/solicitudes-codigo")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ordinary-user")
                    .roles("USER"))
                .param("nombres", "Candidato")
                .param("apellidos", "Prueba")
                .param("email", "candidate@example.test"))
            .andExpect(status().isForbidden());
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
