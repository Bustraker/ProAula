package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.LegalConsentService;
import com.proaula.aula.Service.UsuarioService;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void redirectsNewOAuthUserToUserDashboardAfterProviderAuthentication() throws Exception {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        UsuarioService usuarioService = org.mockito.Mockito.mock(UsuarioService.class);
        LegalConsentService legalConsentService = org.mockito.Mockito.mock(LegalConsentService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
            jwtTokenProvider, 86_400_000L, false, usuarioService, legalConsentService);
        var authentication = new UsernamePasswordAuthenticationToken(
            "oauthuser",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
        request.getSession().setAttribute("oauthAdultConfirmed", Boolean.TRUE);
        LocalDateTime acceptedAt = LocalDateTime.now().minusMinutes(2);
        request.getSession().setAttribute("oauthConsentAcceptedAt", acceptedAt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioService.findByUsername("oauthuser")).thenReturn(usuario);

        when(jwtTokenProvider.generateToken(authentication)).thenReturn("signed-jwt");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
        Cookie jwtCookie = response.getCookie("JWT_TOKEN");
        assertThat(jwtCookie).isNotNull();
        assertThat(jwtCookie.getValue()).isEqualTo("signed-jwt");
        verify(jwtTokenProvider).generateToken(authentication);
        verify(legalConsentService).recordAcceptance(
            usuario, LegalConsentService.CURRENT_VERSION, true, "OAUTH2", acceptedAt);
    }
}
