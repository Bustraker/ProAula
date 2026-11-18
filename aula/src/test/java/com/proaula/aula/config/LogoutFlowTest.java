package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-google-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
    "spring.security.oauth2.client.registration.github.client-id=test-github-client",
    "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
    "spring.security.oauth2.client.registration.discord.client-id=test-discord-client",
    "spring.security.oauth2.client.registration.discord.client-secret=test-discord-secret",
    "spring.security.oauth2.client.registration.facebook.client-id=test-facebook-client",
    "spring.security.oauth2.client.registration.facebook.client-secret=test-facebook-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class LogoutFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logoutInvalidatesTheSessionClearsJwtCookieAndReturnsToLoginPage() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/logout")
                .session(session)
                .cookie(new Cookie("JWT_TOKEN", "expired-session-token"))
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/inicio-de-sesion-mejorado?logout=true"))
            .andExpect(cookie().maxAge("JSESSIONID", 0))
            .andExpect(cookie().maxAge("BustrakerSession", 0))
            .andExpect(cookie().maxAge("JWT_TOKEN", 0));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutNoticeExplainsTheExternalProviderSessionMayRemainActive() throws Exception {
        mockMvc.perform(get("/inicio-de-sesion-mejorado").param("logout", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Has cerrado sesión de ProAula.")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "puede ofrecerte entrar sin volver a escribir su contraseña.")));
    }
}
