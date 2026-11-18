package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proaula.aula.Entity.Rol;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LocalDemoAccountsSeederServiceTest {

    private static final String USER_PASSWORD = "UsuarioDemo#2026";
    private static final String ADMIN_PASSWORD = "AdminDemo#2026!";
    private static final String ADMIN_CODE = "Local-Admin-Access-2026!";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private AdminCodeService adminCodeService;

    @Test
    void createsLocalUserAndAdminAndEnsuresReusableAdminCode() {
        when(usuarioRepository.findByUsername("usuario_demo")).thenReturn(null);
        when(usuarioRepository.findByUsername("admin_demo")).thenReturn(null);

        createSeeder().run(null);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        org.mockito.Mockito.verify(usuarioService, org.mockito.Mockito.times(2)).register(userCaptor.capture());
        assertThat(userCaptor.getAllValues()).extracting(Usuario::getUsername)
            .containsExactly("usuario_demo", "admin_demo");
        assertThat(userCaptor.getAllValues()).extracting(Usuario::getRole)
            .containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(userCaptor.getAllValues()).extracting(Usuario::getPassword)
            .containsExactly(USER_PASSWORD, ADMIN_PASSWORD);
        verify(adminCodeService).ensureDefaultAdminCodes(List.of(ADMIN_CODE));
    }

    @Test
    void refreshesDemoPasswordsWithoutChangingExistingRoles() {
        Usuario user = existingAccount("usuario_demo", "usuario.demo@proaula.local", "ROLE_USER");
        Usuario admin = existingAccount("admin_demo", "admin.demo@proaula.local", "ROLE_ADMIN");
        when(usuarioRepository.findByUsername("usuario_demo")).thenReturn(user);
        when(usuarioRepository.findByUsername("admin_demo")).thenReturn(admin);

        createSeeder().run(null);

        verify(usuarioService).completePasswordSetup("usuario_demo", USER_PASSWORD);
        verify(usuarioService).completePasswordSetup("admin_demo", ADMIN_PASSWORD);
        verify(adminCodeService).ensureDefaultAdminCodes(List.of(ADMIN_CODE));
    }

    @Test
    void refusesToTurnAnExistingUserIntoTheDemoAdmin() {
        Usuario conflictingAdmin = existingAccount(
            "admin_demo", "admin.demo@proaula.local", "ROLE_USER");
        when(usuarioRepository.findByUsername("usuario_demo")).thenReturn(null);
        when(usuarioRepository.findByUsername("admin_demo")).thenReturn(conflictingAdmin);

        assertThatThrownBy(() -> createSeeder().run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("datos o rol incompatibles");
    }

    private LocalDemoAccountsSeederService createSeeder() {
        return new LocalDemoAccountsSeederService(
            usuarioRepository,
            usuarioService,
            adminCodeService,
            "usuario_demo",
            "usuario.demo@proaula.local",
            USER_PASSWORD,
            "admin_demo",
            "admin.demo@proaula.local",
            ADMIN_PASSWORD,
            ADMIN_CODE);
    }

    private Usuario existingAccount(String username, String email, String roleName) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setEmail(email);
        usuario.setProvider("LOCAL");
        usuario.setRole(roleName);
        Rol role = new Rol();
        role.setNombre(roleName);
        usuario.setRoles(Set.of(role));
        return usuario;
    }
}
