package com.proaula.aula.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private CustomOAuth2UserService service;

    @Test
    void createsFacebookAccountWithoutRequiringAnEmailVerifiedAttribute() {
        when(usuarioRepository.findByProviderAndProviderId("FACEBOOK", "fb-123")).thenReturn(null);
        when(usuarioRepository.existsByEmail("z@example.com")).thenReturn(false);
        when(usuarioRepository.existsByUsernameIgnoreCase("userz")).thenReturn(false);
        when(usuarioService.register(any(Usuario.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OAuth2User result = service.processOAuth2User(
            request("facebook"),
            user(Map.of("id", "fb-123", "email", "Z@Example.com", "name", "NoSurname")));

        assertEquals("userz", result.getAttribute("username"));
        verify(usuarioService).register(any(Usuario.class));
    }

    @Test
    void refusesToLinkFacebookAccountToAnExistingEmail() {
        when(usuarioRepository.findByProviderAndProviderId("FACEBOOK", "fb-123")).thenReturn(null);
        when(usuarioRepository.existsByEmail("z@example.com")).thenReturn(true);

        assertThrows(
            OAuth2AuthenticationException.class,
            () -> service.processOAuth2User(
                request("facebook"),
                user(Map.of("id", "fb-123", "email", "z@example.com", "name", "NoSurname"))));

        verify(usuarioService, never()).register(any(Usuario.class));
    }

    @Test
    void createsGoogleAccountWithFallbackWhenProfileOmitsFamilyName() {
        when(usuarioRepository.findByProviderAndProviderId("GOOGLE", "google-123")).thenReturn(null);
        when(usuarioRepository.findByEmail("person@example.com")).thenReturn(null);
        when(usuarioRepository.existsByUsernameIgnoreCase("person")).thenReturn(false);
        when(usuarioService.register(any(Usuario.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.processOAuth2User(
            request("google"),
            user(Map.of(
                "id", "google-123",
                "sub", "google-123",
                "email", "person@example.com",
                "email_verified", true,
                "given_name", "Person")));

        ArgumentCaptor<Usuario> registeredUser = forClass(Usuario.class);
        verify(usuarioService).register(registeredUser.capture());
        assertEquals("Person", registeredUser.getValue().getNombres());
        assertEquals("No especificado", registeredUser.getValue().getApellidos());
    }

    private static OAuth2UserRequest request(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://provider.example/oauth/authorize")
            .tokenUri("https://provider.example/oauth/token")
            .userInfoUri("https://provider.example/user")
            .userNameAttributeName("id")
            .clientName(registrationId)
            .build();
        Instant now = Instant.now();
        OAuth2AccessToken token = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "test-token", now, now.plusSeconds(60));
        return new OAuth2UserRequest(registration, token);
    }

    private static OAuth2User user(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    }
}
