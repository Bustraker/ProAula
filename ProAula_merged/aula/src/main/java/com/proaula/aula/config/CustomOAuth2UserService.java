package com.proaula.aula.config;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.UsuarioService;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public CustomOAuth2UserService(UsuarioRepository usuarioRepository, UsuarioService usuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        processOAuth2User(userRequest, oauth2User);
        return oauth2User;
    }

    private void processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toLowerCase(Locale.ROOT);
        String email = getEmail(oauth2User, registrationId);

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
    }

    private Usuario createUsuarioFromOAuth2User(OAuth2User oauth2User, String registrationId, String email) {
        Usuario usuario = new Usuario();
        usuario.setProvider(registrationId.toUpperCase(Locale.ROOT));
        usuario.setProviderId(getProviderId(oauth2User, registrationId));
        usuario.setEmail(email);
        usuario.setUsername(generateUsername(email));
        usuario.setPassword(UUID.randomUUID().toString());
        usuario.setRole("ROLE_USER");
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

    private String getEmail(OAuth2User user, String registrationId) {
        if ("github".equals(registrationId)) {
            String email = user.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
            return user.getAttribute("login");
        }
        return user.getAttribute("email");
    }

    private String getProviderId(OAuth2User user, String registrationId) {
        if ("google".equals(registrationId)) {
            return user.getAttribute("sub");
        }
        return String.valueOf(user.getAttribute("id"));
    }

    private String getFirstName(OAuth2User user, String registrationId) {
        if ("google".equals(registrationId)) {
            return user.getAttribute("given_name");
        }
        String name = user.getAttribute("name");
        if (name != null && name.contains(" ")) {
            return name.substring(0, name.lastIndexOf(' '));
        }
        return name != null ? name : "Usuario";
    }

    private String getLastName(OAuth2User user, String registrationId) {
        if ("google".equals(registrationId)) {
            return user.getAttribute("family_name");
        }
        String name = user.getAttribute("name");
        if (name != null && name.contains(" ")) {
            return name.substring(name.lastIndexOf(' ') + 1);
        }
        return "";
    }
}
