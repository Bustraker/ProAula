package com.proaula.aula.Service;

import com.proaula.aula.Entity.Rol;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.RolRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;

@Component
public class RolUsuarioSynchronizer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    public RolUsuarioSynchronizer(UsuarioRepository usuarioRepository, RolRepository rolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Usuario usuario : usuarioRepository.findAllWithRoles()) {
            String nombreRol = normalizarRol(usuario.getRole());
            Rol rol = rolRepository.findByNombreIgnoreCase(nombreRol)
                .orElseGet(() -> {
                    Rol nuevoRol = new Rol();
                    nuevoRol.setNombre(nombreRol);
                    return rolRepository.save(nuevoRol);
                });
            if (usuario.getRoles() == null) {
                usuario.setRoles(new HashSet<>());
            }
            boolean changed = usuario.getRoles().size() != 1 || !usuario.getRoles().contains(rol);
            if (changed) {
                usuario.getRoles().clear();
                usuario.getRoles().add(rol);
                usuarioRepository.save(usuario);
            }
        }
    }

    private String normalizarRol(String role) {
        String normalized = role == null || role.isBlank() ? "ROLE_USER" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}
