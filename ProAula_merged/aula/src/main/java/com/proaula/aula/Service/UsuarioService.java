package com.proaula.aula.Service;

import java.util.List;
import java.util.Optional;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.exception.AulaException;
import com.proaula.aula.exception.UsuarioNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Registra un usuario nuevo. Solo debe usarse para CREACIÓN, no actualización.
     * Encripta la contraseña si aún no está hasheada con BCrypt.
     */
    public Usuario register(Usuario usuario) {
        // Normalizar rol
        String role = usuario.getRole();
        if (role == null || role.trim().isEmpty()) {
            usuario.setRole("ROLE_USER");
        } else {
            role = role.trim().toUpperCase();
            if (!role.startsWith("ROLE_")) {
                usuario.setRole("ROLE_" + role);
            } else {
                usuario.setRole(role);
            }
        }

        // FIX: Encriptar solo si la contraseña NO está ya hasheada con BCrypt
        // Esto previene el doble hash en llamadas accidentales
        String password = usuario.getPassword();
        if (password != null && !password.startsWith("$2a$") && !password.startsWith("$2b$")) {
            usuario.setPassword(passwordEncoder.encode(password));
        }

        return usuarioRepository.save(usuario);
    }

    public boolean isAdminUser(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        return usuario != null &&
               usuario.getRole() != null &&
               usuario.getRole().equalsIgnoreCase("ROLE_ADMIN");
    }

    public Usuario getAdminByUsername(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario != null &&
            usuario.getRole() != null &&
            usuario.getRole().equalsIgnoreCase("ROLE_ADMIN")) {
            return usuario;
        }
        return null;
    }

    public Usuario login(String username, String password) {
        Usuario user = usuarioRepository.findByUsername(username);
        if (user == null) {
            throw new UsuarioNotFoundException(username);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AulaException("Contraseña incorrecta", "INVALID_PASSWORD");
        }
        return user;
    }

    public List<Usuario> getAllUsuarios() {
        return usuarioRepository.findAll();
    }

    public Usuario getUsuarioById(Long id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    public void deleteById(Long id) {
        if (id != null) {
            usuarioRepository.deleteById(id);
        }
    }

    public Usuario findByUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public long count() {
        return usuarioRepository.count();
    }

    /**
     * Actualiza solo los datos de perfil (nombres, apellidos, email).
     * NO modifica contraseña ni rol.
     */
    @Transactional
    public Usuario updateUsuario(Long id, Usuario usuarioDetails) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        usuario.setNombres(usuarioDetails.getNombres());
        usuario.setApellidos(usuarioDetails.getApellidos());
        usuario.setEmail(usuarioDetails.getEmail());

        return usuarioRepository.save(usuario);
    }

    /**
     * Cambia la contraseña de un usuario. Encripta la nueva contraseña correctamente.
     */
    @Transactional
    public Usuario changePassword(Long id, String newPassword) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        usuario.setPassword(passwordEncoder.encode(newPassword));

        return usuarioRepository.save(usuario);
    }

    public boolean existsByUsername(String username) {
        return usuarioRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    public Optional<Usuario> findByIdOptional(Long id) {
        return usuarioRepository.findById(id);
    }
}
