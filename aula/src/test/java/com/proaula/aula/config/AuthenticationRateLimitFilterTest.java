package com.proaula.aula.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationRateLimitFilterTest {

    @Test
    void limitsAuthenticationAndRegistrationAttemptsPerRemoteAddress() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter();
        FilterChain chain = (request, response) -> { };

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
            request.setRemoteAddr("192.0.2.10");
            request.addParameter("username", "traveler");
            request.addParameter("password", "valid-password");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest blockedRequest =
                new MockHttpServletRequest("POST", "/api/usuarios/login");
        blockedRequest.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, chain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blockedResponse.getContentAsString())
                .contains("Demasiados intentos");
    }

    @Test
    void rejectsOversizedFormCredentialsBeforeAuthentication() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("192.0.2.11");
        request.addParameter("username", "a".repeat(21));
        request.addParameter("password", "password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/inicio-de-sesion-mejorado?error=true");
    }

    @Test
    void rejectsAdminPasswordsThatExceedBcryptByteLimit() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login");
        request.setRemoteAddr("192.0.2.12");
        request.addParameter("username", "adminuser");
        request.addParameter("password", "ñ".repeat(40));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/admin-login?error=datos-invalidos");
    }
}
