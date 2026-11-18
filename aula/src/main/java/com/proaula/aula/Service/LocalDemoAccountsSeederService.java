package com.proaula.aula.Service;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Component
@Order(3)
@Profile("local & !prod")
@ConditionalOnProperty(prefix = "app.demo-accounts", name = "enabled", havingValue = "true")
public class LocalDemoAccountsSeederService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoAccountsSeederService.class);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final AdminCodeService adminCodeService;
    private final String userUsername;
    private final String userEmail;
    private final String userPassword;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminCode;

    public LocalDemoAccountsSeederService(
            UsuarioRepository usuarioRepository,
            UsuarioService usuarioService,
            AdminCodeService adminCodeService,
            @Value("${app.demo-accounts.user-username}") String userUsername,
            @Value("${app.demo-accounts.user-email}") String userEmail,
            @Value("${app.demo-accounts.user-password}") String userPassword,
            @Value("${app.demo-accounts.admin-username}") String adminUsername,
            @Value("${app.demo-accounts.admin-email}") String adminEmail,
            @Value("${app.demo-accounts.admin-password}") String adminPassword,
            @Value("${app.demo-accounts.admin-code}") String adminCode) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.adminCodeService = adminCodeService;
        this.userUsername = userUsername;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminCode = adminCode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();
        ensureAccount(userUsername, userEmail, userPassword, "Usuario", "Demo", "ROLE_USER");
        ensureAccount(adminUsername, adminEmail, adminPassword, "Administrador", "Demo", "ROLE_ADMIN");
        adminCodeService.ensureDefaultAdminCodes(List.of(adminCode));
        log.info("""
            ================== CUENTAS DE PRUEBA LOCALES ==================
            Usuario:       {}
            Contrasena:    {}
            Admin:         {}
            Contrasena:    {}
            Codigo ADMIN:  {}
            Para iniciar como admin, verifica primero el codigo en /admin-login.
            ================================================================
            """, userUsername, userPassword, adminUsername, adminPassword, adminCode);
    }

    private void ensureAccount(String username, String email, String password,
                               String firstName, String lastName, String expectedRole) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Usuario existing = usuarioRepository.findByUsername(normalizedUsername);
        if (existing == null) {
            Usuario demoUser = new Usuario();
            demoUser.setUsername(normalizedUsername);
            demoUser.setEmail(normalizedEmail);
            demoUser.setPassword(password);
            demoUser.setNombres(firstName);
            demoUser.setApellidos(lastName);
            demoUser.setProvider("LOCAL");
            demoUser.setRole(expectedRole);
            usuarioService.register(demoUser);
            return;
        }

        if (!expectedRole.equalsIgnoreCase(existing.getRole())
                || !normalizedEmail.equalsIgnoreCase(existing.getEmail())
                || !"LOCAL".equalsIgnoreCase(existing.getProvider())
                || existing.getRoles() == null
                || existing.getRoles().stream()
                    .anyMatch(role -> !expectedRole.equalsIgnoreCase(role.getNombre()))) {
            throw new IllegalStateException(
                "La cuenta local de prueba '" + normalizedUsername
                    + "' ya existe con datos o rol incompatibles; no se modificó.");
        }

        usuarioService.completePasswordSetup(normalizedUsername, password);
    }

    private void validateConfiguration() {
        if (isBlank(userUsername) || isBlank(userEmail) || !PasswordPolicy.isValidNewPassword(userPassword)
                || isBlank(adminUsername) || isBlank(adminEmail)
                || !PasswordPolicy.isValidNewPassword(adminPassword) || isBlank(adminCode)) {
            throw new IllegalStateException(
                "Las cuentas de prueba locales requieren usuarios, correos, código y contraseñas seguras "
                    + "(12 a 72 bytes UTF-8).");
        }
        if (userUsername.trim().equalsIgnoreCase(adminUsername.trim())
                || userEmail.trim().equalsIgnoreCase(adminEmail.trim())) {
            throw new IllegalStateException("Las cuentas de prueba de usuario y administrador deben ser diferentes.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
