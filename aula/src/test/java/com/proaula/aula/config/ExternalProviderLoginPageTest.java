package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=placeholder-google-test",
    "spring.security.oauth2.client.registration.google.client-secret=placeholder-google-test",
    "spring.security.oauth2.client.registration.github.client-id=test-github-client",
    "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
    "spring.security.oauth2.client.registration.discord.client-id=test-discord-client",
    "spring.security.oauth2.client.registration.discord.client-secret=test-discord-secret",
    "spring.security.oauth2.client.registration.facebook.client-id=test-facebook-client",
    "spring.security.oauth2.client.registration.facebook.client-secret=test-facebook-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class ExternalProviderLoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageOnlyOffersProvidersWithConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/inicio-de-sesion-mejorado"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/oauth2/authorization/github\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/oauth2/authorization/discord\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/oauth2/authorization/facebook\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("/oauth2/authorization/google"))));
    }

    @Test
    void oauthFailureShowsProviderSpecificHelpInsteadOfPasswordError() throws Exception {
        mockMvc.perform(get("/inicio-de-sesion-mejorado").param("oauthError", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "No se pudo completar el inicio con el proveedor externo.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Usuario o contraseña incorrectos."))));
    }

    @Test
    void authenticationPagesShowTheirDistinctPurposeScenes() throws Exception {
        mockMvc.perform(get("/inicio-de-sesion-mejorado"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("auth-scene--access")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ENTRAR A MI CUENTA")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("IDENTIDAD VERIFICADA")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("route-map__bus"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("style_auth_experience.css?v=20261024-3")));

        mockMvc.perform(get("/registro-mejorado"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("auth-scene--profile")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("PERFIL DE VIAJERO")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("CREACIÓN DE TU CUENTA")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("route-map__bus"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("style_auth_experience.css?v=20261024-3")));
    }

    @Test
    void configuredProvidersRedirectToAuthorizationEndpointsWithLocalCallbacks() throws Exception {
        assertAuthorizationRedirect(
            "/oauth2/authorization/google",
            "accounts.google.com",
            "http://localhost/login/oauth2/code/google",
            "openid profile email");
        assertAuthorizationRedirect(
            "/oauth2/authorization/github",
            "github.com",
            "http://localhost/login/oauth2/code/github",
            "user:email");
        assertAuthorizationRedirect(
            "/oauth2/authorization/discord",
            "discord.com",
            "http://localhost/login/oauth2/code/discord",
            "identify email");
        assertAuthorizationRedirect(
            "/oauth2/authorization/facebook",
            "www.facebook.com",
            "http://localhost/login/oauth2/code/facebook",
            "email public_profile");
    }

    @Test
    void configuredProvidersRedirectToHttpsCallbacksWhenLocalApplicationUsesHttps() throws Exception {
        assertAuthorizationRedirect(
            "/oauth2/authorization/google",
            "accounts.google.com",
            "https://localhost:5679/login/oauth2/code/google",
            "openid profile email",
            true);
        assertAuthorizationRedirect(
            "/oauth2/authorization/github",
            "github.com",
            "https://localhost:5679/login/oauth2/code/github",
            "user:email",
            true);
        assertAuthorizationRedirect(
            "/oauth2/authorization/discord",
            "discord.com",
            "https://localhost:5679/login/oauth2/code/discord",
            "identify email",
            true);
        assertAuthorizationRedirect(
            "/oauth2/authorization/facebook",
            "www.facebook.com",
            "https://localhost:5679/login/oauth2/code/facebook",
            "email public_profile",
            true);
    }

    private void assertAuthorizationRedirect(String path, String expectedHost, String expectedCallback,
                                             String expectedScope) throws Exception {
        assertAuthorizationRedirect(path, expectedHost, expectedCallback, expectedScope, false);
    }

    private void assertAuthorizationRedirect(String path, String expectedHost, String expectedCallback,
                                             String expectedScope, boolean secure) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(request -> {
                request.setSecure(secure);
                request.setScheme(secure ? "https" : "http");
                request.setServerPort(secure ? 5679 : 80);
                return request;
            }))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().exists("Location"))
            .andReturn();

        URI authorizationUri = URI.create(result.getResponse().getHeader("Location"));
        assertThat(authorizationUri.getHost()).isEqualTo(expectedHost);
        assertThat(UriComponentsBuilder.fromUri(authorizationUri)
            .build(true)
            .getQueryParams()
            .getFirst("redirect_uri")).isEqualTo(expectedCallback);
        assertThat(UriComponentsBuilder.fromUri(authorizationUri)
            .build(true)
            .getQueryParams()
            .getFirst("scope"))
            .satisfies(scope -> assertThat(URLDecoder.decode(scope, StandardCharsets.UTF_8))
                .isEqualTo(expectedScope));
    }
}
