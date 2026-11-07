package com.proaula.aula.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class AccountFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private UsuarioService usuarioService;

    @Test
    void registrationLoginProtectedPageAndLogoutWorkAsOneJourney() throws Exception {
        mockMvc.perform(get("/registro-mejorado"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));

        mockMvc.perform(post("/registro")
                        .with(csrf())
                        .param("nombres", "Usuario")
                        .param("apellidos", "Verificacion")
                        .param("username", "FlowUser")
                        .param("email", "FlowUser@Example.Test")
                        .param("password", "safe-password-26")
                        .param("passwordConfirm", "safe-password-26")
                        .param("role", "USER")
                        .param("terminos", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/inicio-de-sesion-mejorado?registrado=true"));

        Usuario usuario = usuarioRepository.findByUsername("flowuser");
        assertThat(usuario).isNotNull();
        assertThat(usuario.getUsername()).isEqualTo("flowuser");
        assertThat(usuario.getEmail()).isEqualTo("flowuser@example.test");
        assertThat(usuario.getRole()).isEqualTo("ROLE_USER");
        assertThat(passwordEncoder.matches("safe-password-26", usuario.getPassword())).isTrue();

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "FLOWUSER")
                        .param("password", "safe-password-26"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Bustraker")));

        MvcResult logoutResult = mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/inicio-de-sesion-mejorado?logout=true"))
                .andReturn();
        assertThat(logoutResult.getResponse().getHeaders("Set-Cookie").toString())
                .contains("JWT_TOKEN=");

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost/inicio-de-sesion-mejorado"));
    }

    @Test
    void secureRequestsIncludeSpringSecurityBrowserProtectionHeaders() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void publicRegistrationCannotCreateAnAdministratorEvenWhenRoleIsSubmitted() throws Exception {
        mockMvc.perform(post("/registro")
                        .with(csrf())
                        .param("nombres", "Candidato")
                        .param("apellidos", "Prueba")
                        .param("username", "adminprobe")
                        .param("email", "adminprobe@example.test")
                        .param("password", "safe-password-26")
                        .param("passwordConfirm", "safe-password-26")
                        .param("role", "ADMIN")
                        .param("adminCode", "forged-admin-code")
                        .param("terminos", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/inicio-de-sesion-mejorado?registrado=true"));

        Usuario usuario = usuarioRepository.findByUsername("adminprobe");
        assertThat(usuario).isNotNull();
        assertThat(usuario.getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void registrationRejectsScriptLikeUsernamesAndWeakPasswords() throws Exception {
        mockMvc.perform(post("/registro")
                        .with(csrf())
                        .param("nombres", "Usuario")
                        .param("apellidos", "Prueba")
                        .param("username", "<script>alert")
                        .param("email", "script@example.test")
                        .param("password", "short")
                        .param("terminos", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(
                                "Revisa los datos ingresados y corrige los campos marcados.")));

        assertThat(usuarioRepository.findByUsername("<script>alert")).isNull();
        assertThat(usuarioRepository.findByEmail("script@example.test")).isNull();
    }

    @Test
    void registrationRequiresMatchingPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/registro")
                        .with(csrf())
                        .param("nombres", "Usuario")
                        .param("apellidos", "Prueba")
                        .param("username", "mismatch")
                        .param("email", "mismatch@example.test")
                        .param("password", "secure-password-2026")
                        .param("passwordConfirm", "different-password-2026")
                        .param("terminos", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Las contraseñas no coinciden.")));

        assertThat(usuarioRepository.findByUsername("mismatch")).isNull();
    }

    @Test
    void loginPageDoesNotReflectAUserSuppliedUsername() throws Exception {
        mockMvc.perform(get("/inicio-de-sesion-mejorado")
                        .param("username", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));
    }

    @Test
    void apiLoginRejectsSqlInjectionLikeCredentialsWithoutAuthenticating() throws Exception {
        mockMvc.perform(post("/api/usuarios/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "' OR '1'='1",
                                  "password": "not-a-valid-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.error").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void apiRegistrationRejectsWeakPasswords() throws Exception {
        mockMvc.perform(post("/api/usuarios/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "newtraveler",
                                  "password": "short",
                                  "nombres": "Nuevo",
                                  "apellidos": "Viajero",
                                  "email": "newtraveler@example.test"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.findByUsername("newtraveler")).isNull();
    }

    @Test
    void registrationAndApiLoginRejectPasswordsThatExceedBcryptByteLimit() throws Exception {
        String oversizedUtf8Password = "ñ".repeat(40);

        mockMvc.perform(post("/registro")
                        .with(csrf())
                        .param("nombres", "Usuario")
                        .param("apellidos", "Prueba")
                        .param("username", "utf8register")
                        .param("email", "utf8register@example.test")
                        .param("password", oversizedUtf8Password)
                        .param("passwordConfirm", oversizedUtf8Password)
                        .param("terminos", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(
                                "no puede superar 72 bytes en UTF-8")));
        assertThat(usuarioRepository.findByUsername("utf8register")).isNull();

        mockMvc.perform(post("/api/usuarios/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "traveler",
                                  "password": "%s"
                                }
                                """.formatted(oversizedUtf8Password)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordPolicyIsEnforcedAtEveryServicePasswordSettingBoundary() {
        Usuario usuario = createPasswordPolicyUser("servicepolicy", false);

        assertThatThrownBy(() -> usuarioService.changePassword(usuario.getId(), "short-pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 y 72 caracteres");
        assertThatThrownBy(() -> usuarioService.changePassword(usuario.getId(), "ñ".repeat(40)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes en UTF-8");
        assertThatThrownBy(() -> usuarioService.completePasswordSetup(usuario.getUsername(), "short-pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 y 72 caracteres");
        assertThatThrownBy(() -> usuarioService.completePasswordSetup(usuario.getUsername(), "ñ".repeat(40)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes en UTF-8");
    }

    @Test
    void profilePasswordChangeRejectsWeakPasswordWithoutChangingCurrentPassword() throws Exception {
        Usuario usuario = createPasswordPolicyUser("profilepolicy", false);
        String oldPassword = "existing-password-26";

        mockMvc.perform(post("/perfil/cambiar-password")
                        .with(user(usuario.getUsername()))
                        .with(csrf())
                        .param("passwordActual", oldPassword)
                        .param("passwordNuevo", "short-pass")
                        .param("passwordConfirmar", "short-pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/perfil"));

        Usuario persisted = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(oldPassword, persisted.getPassword())).isTrue();
    }

    @Test
    void oauthPasswordSetupRejectsWeakPasswordAndKeepsSetupRequired() throws Exception {
        Usuario usuario = createPasswordPolicyUser("oauthpolicy", true);
        String oldPassword = "existing-password-26";

        mockMvc.perform(post("/oauth2/complete-password")
                        .with(user(usuario.getUsername()))
                        .with(csrf())
                        .param("password", "short-pass")
                        .param("confirmPassword", "short-pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/complete-password"));

        Usuario persisted = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(persisted.isPasswordSetupRequired()).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, persisted.getPassword())).isTrue();
    }

    private Usuario createPasswordPolicyUser(String username, boolean passwordSetupRequired) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setEmail(username + "@example.test");
        usuario.setPassword("existing-password-26");
        usuario.setNombres("Password");
        usuario.setApellidos("Policy");
        usuario.setRole("ROLE_USER");
        usuario.setProvider("LOCAL");
        usuario.setPasswordSetupRequired(passwordSetupRequired);
        return usuarioService.register(usuario);
    }
}
