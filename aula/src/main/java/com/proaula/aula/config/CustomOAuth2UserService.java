package com.proaula.aula.config;

import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_GITHUB = "github";
    private static final String ATTRIBUTE_EMAIL = "email";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public CustomOAuth2UserService(UsuarioRepository usuarioRepository, UsuarioService usuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oauth2User);
    }

    public OidcUser loadOidcUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = new OidcUserService().loadUser(userRequest);
        OAuth2User localUser = processOAuth2User(userRequest, oidcUser);
        return new LocalOidcUser(oidcUser, localUser);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toLowerCase(Locale.ROOT);
        String email = getEmail(userRequest, oauth2User, registrationId);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("No se pudo obtener el email del proveedor OAuth2");
        }

        Usuario usuario = usuarioRepository.findByEmail(email);
        if (usuario == null) {
            usuario = createUsuarioFromOAuth2User(oauth2User, registrationId, email);
        } else {
            usuario.setProvider(registrationId.toUpperCase(Locale.ROOT));
            usuario.setProviderId(getProviderId(oauth2User, registrationId));
            usuarioRepository.save(usuario);
        }

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("username", usuario.getUsername());
        attributes.put("sub", usuario.getUsername());
        Set<GrantedAuthority> authorities = new HashSet<>(oauth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return new DefaultOAuth2User(authorities, attributes, "username");
    }

    private Usuario createUsuarioFromOAuth2User(OAuth2User oauth2User, String registrationId, String email) {
        Usuario usuario = new Usuario();
        usuario.setProvider(registrationId.toUpperCase(Locale.ROOT));
        usuario.setProviderId(getProviderId(oauth2User, registrationId));
        usuario.setEmail(email);
        usuario.setUsername(generateUsername(email));
        usuario.setPassword(UUID.randomUUID().toString());
        usuario.setRole("ROLE_USER");
        usuario.setPasswordSetupRequired(true);
        usuario.setNombres(getFirstName(oauth2User, registrationId));
        usuario.setApellidos(getLastName(oauth2User, registrationId));
        return usuarioService.register(usuario);
    }

    private String generateUsername(String email) {
        String prefix = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        prefix = prefix.replaceAll("[^A-Za-z0-9]", "");
        if (prefix.isBlank()) {
            prefix = "user";
        }

        String username = prefix;
        int suffix = 1;
        while (usuarioRepository.existsByUsername(username)) {
            username = prefix + suffix++;
        }
        return username;
    }

    private String getEmail(OAuth2UserRequest userRequest, OAuth2User user, String registrationId) {
        if (PROVIDER_GITHUB.equals(registrationId)) {
            String email = user.getAttribute(ATTRIBUTE_EMAIL);
            if (email != null && !email.isBlank()) {
                return email;
            }
            return fetchGithubEmail(userRequest);
        }
        return user.getAttribute(ATTRIBUTE_EMAIL);
    }

    private String getProviderId(OAuth2User user, String registrationId) {
        if (PROVIDER_GOOGLE.equals(registrationId)) {
            return user.getAttribute("sub");
        }
        return String.valueOf(user.getAttribute("id"));
    }

    private String getFirstName(OAuth2User user, String registrationId) {
        if (PROVIDER_GOOGLE.equals(registrationId)) {
            return user.getAttribute("given_name");
        }
        if ("discord".equals(registrationId)) {
            String displayName = user.getAttribute("global_name");
            if (displayName == null || displayName.isBlank()) {
                displayName = user.getAttribute("username");
            }
            return displayName != null ? displayName : "Usuario";
        }
        String name = user.getAttribute("name");
        if (name != null && name.contains(" ")) {
            return name.substring(0, name.lastIndexOf(' '));
        }
        return name != null ? name : "Usuario";
    }

    private String getLastName(OAuth2User user, String registrationId) {
        if (PROVIDER_GOOGLE.equals(registrationId)) {
            return user.getAttribute("family_name");
        }
        String name = user.getAttribute("name");
        if (name != null && name.contains(" ")) {
            return name.substring(name.lastIndexOf(' ') + 1);
        }
        return "";
    }

    private String fetchGithubEmail(OAuth2UserRequest userRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(userRequest.getAccessToken().getTokenValue());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                ResponseEntity<String> response = new RestTemplate().exchange(
                    "https://api.github.com/user/emails", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
                String body = response.getBody();
                if (body == null || body.isBlank()) {
                return null;
            }
            List<Map<String, Object>> emails = new ObjectMapper().readValue(
                    body, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> entry : emails) {
                if (Boolean.TRUE.equals(entry.get("primary"))
                        && Boolean.TRUE.equals(entry.get("verified"))) {
                    return (String) entry.get("email");
                }
            }
            return emails.stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.get("verified")))
                    .map(entry -> (String) entry.get("email"))
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst().orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private static final class LocalOidcUser implements OidcUser {
        private final OidcUser delegate;
        private final OAuth2User localUser;

        private LocalOidcUser(OidcUser delegate, OAuth2User localUser) {
            this.delegate = delegate;
            this.localUser = localUser;
        }

        @Override public Map<String, Object> getAttributes() { return localUser.getAttributes(); }
        @Override public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() { return localUser.getAuthorities(); }
        @Override public String getName() { return localUser.getName(); }
        @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
        @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
        @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    }
}
