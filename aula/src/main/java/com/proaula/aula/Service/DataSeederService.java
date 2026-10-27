package com.proaula.aula.Service;

import com.proaula.aula.Barrios.Localidades;
import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Order(1)
public class DataSeederService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);

    private final BarrioRepository barrioRepository;
    private final BarrioService barrioService;
    private final AdminCodeService adminCodeService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap-admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${app.admin.approver-email:}")
    private String adminApproverEmail;

    public DataSeederService(BarrioRepository barrioRepository,
                             BarrioService barrioService,
                             AdminCodeService adminCodeService,
                             UsuarioRepository usuarioRepository,
                             UsuarioService usuarioService) {
        this.barrioRepository = barrioRepository;
        this.barrioService = barrioService;
        this.adminCodeService = adminCodeService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        adminCodeService.migrateLegacyAdminCodes();
        seedBarrios();
        bootstrapAdminIfConfigured();
    }

    private void seedBarrios() {
        Set<String> existingNames = barrioRepository.findAll().stream()
            .map(Barrio::getNombre)
            .filter(nombre -> nombre != null)
            .map(this::normalizeName)
            .collect(Collectors.toSet());

        int savedCount = 0;
        for (String nombre : Localidades.obtenerTodosLosBarrios()) {
            if (existingNames.add(normalizeName(nombre))) {
                Barrio barrio = new Barrio();
                barrio.setNombre(nombre.trim());
                barrio.setLocalidad("Cartagena");
                barrioRepository.save(barrio);
                savedCount++;
            }
        }

        log.info("Barrios de referencia inicializados; {} nuevos registros", savedCount);
        barrioService.inicializarBarrios();
    }

    private void bootstrapAdminIfConfigured() {
        if (!bootstrapAdminEnabled) {
            return;
        }
        if (isBlank(bootstrapAdminUsername) || isBlank(bootstrapAdminEmail)
                || isBlank(bootstrapAdminPassword) || isBlank(adminApproverEmail)
                || bootstrapAdminPassword.length() < 12 || bootstrapAdminPassword.length() > 72
                || bootstrapAdminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                "El aprovisionamiento requiere usuario, correo aprobador y contraseña segura (12 a 72 bytes UTF-8)");
        }
        if (!bootstrapAdminEmail.trim().equalsIgnoreCase(adminApproverEmail.trim())) {
            throw new IllegalStateException(
                "El correo del administrador inicial debe coincidir con ADMIN_APPROVER_EMAIL.");
        }

        String username = bootstrapAdminUsername.trim();
        if (usuarioRepository.existsByUsernameIgnoreCase(username)) {
            if (!usuarioService.isAdminUser(username)) {
                throw new IllegalStateException(
                    "El usuario configurado para el aprovisionamiento existe y no tiene rol de administrador");
            }
            Usuario existingAdmin = usuarioRepository.findByUsername(username);
            if (existingAdmin == null || existingAdmin.getEmail() == null
                    || !existingAdmin.getEmail().equalsIgnoreCase(adminApproverEmail.trim())) {
                throw new IllegalStateException(
                    "El administrador configurado no coincide con ADMIN_APPROVER_EMAIL.");
            }
            log.info("El administrador configurado ya existe; no se realizaron cambios.");
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsername(username);
        admin.setEmail(bootstrapAdminEmail.trim());
        admin.setPassword(bootstrapAdminPassword);
        admin.setNombres("Administrador");
        admin.setApellidos("Sistema");
        admin.setProvider("LOCAL");
        admin.setRole("ROLE_ADMIN");
        usuarioService.register(admin);
        log.info("Se aprovisionó el administrador inicial configurado.");
    }

    private String normalizeName(String name) {
        return Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("ñ", "n")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
