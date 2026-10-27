package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class CustomOAuth2UserServiceTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;
    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioService = mock(UsuarioService.class);
        customOAuth2UserService = new CustomOAuth2UserService(usuarioRepository, usuarioService);
        when(usuarioRepository.findByProviderAndProviderId(any(), any())).thenReturn(null);
        when(usuarioRepository.findByEmail(any())).thenReturn(null);
        when(usuarioRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(usuarioService.register(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void facebookCreatesAccountFromGrantedEmailWithoutRequiringUnsupportedVerificationClaim() {
        OAuth2User user = oauth2User(Map.of(
            "id", "facebook-user-id",
            "email", "a@example.test",
            "name", "Ada"
        ));

        OAuth2User authenticated = customOAuth2UserService.processOAuth2User(
            userRequest("facebook"), user);
        ArgumentCaptor<Usuario> capturedUser = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).register(capturedUser.capture());

        assertThat(authenticated.getName()).isEqualTo("usera");
        assertThat(capturedUser.getValue().getNombres()).isEqualTo("Ada");
        assertThat(capturedUser.getValue().getApellidos()).isEqualTo("No especificado");
        assertThat(capturedUser.getValue().isPasswordSetupRequired()).isTrue();
    }

    @Test
    void discordCreatesAccountWithValidRequiredProfileFields() {
        OAuth2User user = oauth2User(Map.of(
            "id", "discord-user-id",
            "email", "u@example.test",
            "verified", true,
            "username", "Rider"
        ));

        OAuth2User authenticated = customOAuth2UserService.processOAuth2User(
            userRequest("discord"), user);
        ArgumentCaptor<Usuario> capturedUser = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).register(capturedUser.capture());

        assertThat(authenticated.getName()).isEqualTo("useru");
        assertThat(capturedUser.getValue().getUsername()).isEqualTo("useru");
        assertThat(capturedUser.getValue().getNombres()).isEqualTo("Rider");
        assertThat(capturedUser.getValue().getApellidos()).isEqualTo("No especificado");
    }

    @Test
    void discordRejectsUnverifiedEmail() {
        OAuth2User user = oauth2User(Map.of(
            "id", "discord-user-id",
            "email", "rider@example.test",
            "verified", false,
            "username", "Rider"
        ));

        assertThatThrownBy(() -> customOAuth2UserService.processOAuth2User(
            userRequest("discord"), user))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void facebookDoesNotLinkAnUnverifiedProviderEmailToAnExistingLocalAccount() {
        when(usuarioRepository.existsByEmail("a@example.test")).thenReturn(true);
        OAuth2User user = oauth2User(Map.of(
            "id", "facebook-user-id",
            "email", "a@example.test",
            "name", "Ada"
        ));

        assertThatThrownBy(() -> customOAuth2UserService.processOAuth2User(
            userRequest("facebook"), user))
            .isInstanceOf(OAuth2AuthenticationException.class);
        verify(usuarioService, never()).register(any(Usuario.class));
    }

    @Test
    void createsGoogleAccountWithFallbackWhenProfileOmitsFamilyName() {
        OAuth2User user = oauth2User(Map.of(
            "id", "google-user-id",
            "sub", "google-user-id",
            "email", "person@example.test",
            "email_verified", true,
            "given_name", "Person"
        ));

        customOAuth2UserService.processOAuth2User(userRequest("google"), user);
        ArgumentCaptor<Usuario> registeredUser = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).register(registeredUser.capture());

        assertThat(registeredUser.getValue().getNombres()).isEqualTo("Person");
        assertThat(registeredUser.getValue().getApellidos()).isEqualTo("No especificado");
    }

    private OAuth2User oauth2User(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "id");
    }

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client-id")
            .clientSecret("test-client-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("identify", "email")
            .authorizationUri("https://provider.example.test/oauth/authorize")
            .tokenUri("https://provider.example.test/oauth/token")
            .userInfoUri("https://provider.example.test/user")
            .userNameAttributeName("id")
            .clientName(registrationId)
            .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-access-token",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, accessToken);
    }
}
