package com.proaula.aula.Service;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.exception.UsuarioNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new UsuarioNotFoundException(username);
        }

        return new User(
            usuario.getUsername(),
            usuario.getPassword(),
            getAuthorities(usuario)
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Usuario usuario) {
        Set<String> roles = usuario.getRoles().stream()
            .map(role -> normalizarRol(role.getNombre()))
            .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            roles = Collections.singleton(normalizarRol(usuario.getRole()));
        }
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private String normalizarRol(String role) {
        String normalized = role == null || role.isBlank() ? "ROLE_USER" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}