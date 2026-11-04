package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void redirectsNewOAuthUserToUserDashboardAfterProviderAuthentication() throws Exception {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(jwtTokenProvider, 86_400_000L, false);
        var authentication = new UsernamePasswordAuthenticationToken(
            "oauthuser",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.generateToken(authentication)).thenReturn("signed-jwt");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
        Cookie jwtCookie = response.getCookie("JWT_TOKEN");
        assertThat(jwtCookie).isNotNull();
        assertThat(jwtCookie.getValue()).isEqualTo("signed-jwt");
        verify(jwtTokenProvider).generateToken(authentication);
    }
}
