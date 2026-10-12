package com.proaula.aula.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
                .andExpect(header().string("Location", "/"))
                .andReturn();
        assertThat(logoutResult.getResponse().getHeaders("Set-Cookie").toString())
                .contains("JWT_TOKEN=");

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost/inicio-de-sesion-mejorado"));
    }
}
