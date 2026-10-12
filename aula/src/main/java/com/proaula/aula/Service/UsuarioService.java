package com.proaula.aula.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Entity.Rol;
import com.proaula.aula.Repository.RolRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Repository.ViajeRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.Repository.ConductorRepository;
import com.proaula.aula.exception.AulaException;
import com.proaula.aula.exception.UsuarioNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
public class UsuarioService {
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private PasajeRepository pasajeRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private ConductorRepository conductorRepository;

    // Registra usuario y encripta contraseña no hasheada
    // Normaliza rol a ROLE_USER o ROLE_ADMIN
    @Transactional
    public Usuario register(Usuario usuario) {
        usuario.setUsername(normalizarIdentificador(usuario.getUsername()));
        usuario.setEmail(normalizarIdentificador(usuario.getEmail()));

        String role = usuario.getRole();
        if (role == null || role.trim().isEmpty()) {
            usuario.setRole("ROLE_USER");
        } else {
            role = role.trim().toUpperCase(Locale.ROOT);
            if (!role.startsWith("ROLE_")) {
                usuario.setRole("ROLE_" + role);
            } else {
                usuario.setRole(role);
            }
        }

        String password = usuario.getPassword();
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new IllegalArgumentException("La contraseña debe tener entre 6 y 72 caracteres");
        }
        usuario.setPassword(passwordEncoder.encode(password));

        String nombreRol = usuario.getRole();
        Rol rol = rolRepository.findByNombreIgnoreCase(nombreRol)
            .orElseGet(() -> {
                Rol nuevoRol = new Rol();
                nuevoRol.setNombre(nombreRol);
                return rolRepository.save(nuevoRol);
            });
        if (usuario.getRoles() == null) {
            usuario.setRoles(new HashSet<>());
        }
        usuario.getRoles().clear();
        usuario.getRoles().add(rol);

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

    @Transactional
    public void deleteById(Long id) {
        if (id == null || !usuarioRepository.existsById(id)) {
            return;
        }
        if (hasRelatedRecords(id)) {
            throw new AulaException(
                "No se puede eliminar el usuario porque tiene viajes, pasajes, notificaciones o un perfil de conductor asociado.",
                "RESOURCE_IN_USE");
        }
        usuarioRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean hasRelatedRecords(Long id) {
        return id != null && (viajeRepository.existsByUsuario_Id(id)
                || pasajeRepository.existsByUsuario_Id(id)
                || notificacionRepository.existsByUsuario_Id(id)
                || conductorRepository.existsByUsuario_Id(id));
    }

    public Usuario findByUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public long count() {
        return usuarioRepository.count();
    }

    // Actualiza perfil básico sin cambiar rol o contraseña
    @Transactional
    public Usuario updateUsuario(Long id, Usuario usuarioDetails) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        String emailNormalizado = normalizarIdentificador(usuarioDetails.getEmail());
        if (emailNormalizado == null || emailNormalizado.isBlank()
                || usuarioDetails.getNombres() == null || usuarioDetails.getNombres().isBlank()
                || usuarioDetails.getApellidos() == null || usuarioDetails.getApellidos().isBlank()) {
            throw new AulaException("Nombres, apellidos y correo son obligatorios", "VALIDATION_ERROR");
        }
        if (!emailNormalizado.equalsIgnoreCase(usuario.getEmail())
                && usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new AulaException("El correo electrónico ya está registrado", "EMAIL_ALREADY_EXISTS");
        }
        usuario.setNombres(usuarioDetails.getNombres().trim());
        usuario.setApellidos(usuarioDetails.getApellidos().trim());
        usuario.setEmail(emailNormalizado);

        return usuarioRepository.save(usuario);
    }

    // Cambia y encripta contraseña existente
    @Transactional
    public Usuario changePassword(Long id, String newPassword) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

        usuario.setPassword(passwordEncoder.encode(newPassword));

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario completePasswordSetup(String username, String newPassword) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new UsuarioNotFoundException(username);
        }

        usuario.setPassword(passwordEncoder.encode(newPassword));
        usuario.setPasswordSetupRequired(false);
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

    private String normalizarIdentificador(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
