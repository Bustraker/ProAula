package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
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
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("username", "oauthuser"), "username");
        var authentication = new OAuth2AuthenticationToken(principal, authorities, "google");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
        request.getSession().setAttribute("oauthAdultConfirmed", Boolean.TRUE);
        request.getSession().setAttribute("oauthConsentSource", "google");
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

    @Test
    void rejectsConsentRecordedForADifferentExternalProvider() throws Exception {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        UsuarioService usuarioService = org.mockito.Mockito.mock(UsuarioService.class);
        LegalConsentService legalConsentService = org.mockito.Mockito.mock(LegalConsentService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
            jwtTokenProvider, 86_400_000L, false, usuarioService, legalConsentService);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("username", "oauthuser"), "username");
        var authentication = new OAuth2AuthenticationToken(principal, authorities, "github");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
        request.getSession().setAttribute("oauthAdultConfirmed", Boolean.TRUE);
        request.getSession().setAttribute("oauthConsentSource", "google");
        request.getSession().setAttribute("oauthConsentAcceptedAt", LocalDateTime.now());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioService.findByUsername("oauthuser")).thenReturn(usuario);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
            .isEqualTo("/inicio-de-sesion-mejorado?consentRequired=true");
        org.mockito.Mockito.verifyNoInteractions(legalConsentService, jwtTokenProvider);
    }

    @Test
    void doesNotDuplicateAnExistingCurrentConsentOnOAuthLogin() throws Exception {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        UsuarioService usuarioService = org.mockito.Mockito.mock(UsuarioService.class);
        LegalConsentService legalConsentService = org.mockito.Mockito.mock(LegalConsentService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
            jwtTokenProvider, 86_400_000L, false, usuarioService, legalConsentService);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("username", "oauthuser"), "username");
        var authentication = new OAuth2AuthenticationToken(principal, authorities, "google");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
        request.getSession().setAttribute("oauthAdultConfirmed", Boolean.TRUE);
        request.getSession().setAttribute("oauthConsentSource", "google");
        request.getSession().setAttribute("oauthConsentAcceptedAt", LocalDateTime.now());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioService.findByUsername("oauthuser")).thenReturn(usuario);
        when(legalConsentService.hasCurrentConsent("oauthuser")).thenReturn(true);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("signed-jwt");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
        verify(legalConsentService).hasCurrentConsent("oauthuser");
        verify(legalConsentService, never()).recordAcceptance(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }
}
