package com.proaula.aula.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.github.javafaker.Faker;
import com.proaula.aula.Barrios.Localidades;
import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Entity.Viaje;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Repository.ViajeRepository;

@Service
public class DataSeederService implements CommandLineRunner {
    @Autowired
    private BarrioService barrioService;
    @Autowired
    private AdminCodeService adminCodeService;
    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);
    
    @Autowired
    private BarrioRepository barrioRepository;
    
    @Autowired
    private BusRepository busRepository;
    
    @Autowired
    private RutaRepository rutaRepository;
    
    @Autowired
    private ParadaRepository paradaRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ViajeRepository viajeRepository;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private Faker faker = new Faker(new java.util.Locale.Builder().setLanguage("es").setRegion("CO").build()); // Configurado para datos en español de Colombia
    private Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        try {
            // Cargar todos los barrios de Cartagena en la base de datos
            log.info("Inicializando barrios de Cartagena desde Localidades.java...");
            List<String> allBarrios = Localidades.obtenerTodosLosBarrios();
            Set<String> barriosExistentesNormalizados = barrioRepository.findAll().stream()
                .map(b -> b.getNombre().toLowerCase()
                    .replace("á", "a").replace("é", "e").replace("í", "i")
                    .replace("ó", "o").replace("ú", "u").replace("ñ", "n"))
                .collect(Collectors.toSet());

            // Guardar códigos de administrador en la base de datos
            log.info("Verificando códigos de administrador en la base de datos...");
            adminCodeService.ensureDefaultAdminCodes(List.of("ADMIN2026"));

            int barriosGuardados = 0;
            for (String barrioNombre : allBarrios) {
                String nombreNormalizado = barrioNombre.toLowerCase()
                    .replace("á", "a").replace("é", "e").replace("í", "i")
                    .replace("ó", "o").replace("ú", "u").replace("ñ", "n");

                if (!barriosExistentesNormalizados.contains(nombreNormalizado)) {
                    Barrio barrio = new Barrio();
                    barrio.setNombre(barrioNombre.trim());
                    barrio.setLocalidad("Cartagena");
                    // Las coordenadas se asignarán después desde BarrioService
                    barrioRepository.save(barrio);
                    barriosGuardados++;
                }
            }
            log.info("Completado: {} barrios guardados en la base de datos", barriosGuardados);
            
            // Asignar coordenadas GPS reales
            barrioService.inicializarBarrios();
            
            log.info("Estado actual: {} rutas, {} buses, {} usuarios, {} barrios", 
                rutaRepository.count(), busRepository.count(), usuarioRepository.count(), barrioRepository.count());

            int usuariosToCreate = 3000;  // 3.000 usuarios
            log.info("No se generan rutas, buses ni paradas de demostración: deben cargarse datos verificados.");

            // Crear usuario administrador por defecto si no existe
            if (!usuarioRepository.existsByUsername("admin")) {
                log.info("Creando administrador...");
                Usuario admin = new Usuario();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ROLE_ADMIN");
                admin.setNombres("Administrador");
                admin.setApellidos("Sistema");
                admin.setEmail("admin@bustraker.edu.co");
                usuarioRepository.save(admin);
                log.info("Completado: Administrador creado (user: admin, password: admin123)");
            } else {
                log.info("Completado: El administrador 'admin' ya existe. No se creó un nuevo admin.");
            }

            seedUsuarios(usuariosToCreate);
            seedViajesEjemplo();

            // Normalizar roles en la base de datos
            normalizarRolesEnBaseDatos();

            // Resumen final
            long totalRutas = rutaRepository.count();
            long totalBuses = busRepository.count();
            long totalUsuarios = usuarioRepository.count();
            long totalParadas = paradaRepository.count();
            long totalRegistros = totalRutas + totalBuses + totalUsuarios + totalParadas;
            
            log.info("================================================");
            log.info("CARGA DE DATOS COMPLETADA EXITOSAMENTE");
            log.info("================================================");
            log.info("RESUMEN:");
            log.info("   Rutas: {}", totalRutas);
            log.info("   Buses: {}", totalBuses);
            log.info("   Usuarios: {}", totalUsuarios);
            log.info("   Paradas: {}", totalParadas);
            log.info("   TOTAL REGISTROS: {}", totalRegistros);
            log.info("================================================");
            log.info("CREDENCIALES POR DEFECTO:");
            log.info("   Admin: user=admin, pass=admin123");
            log.info("   Usuario: user=[cualquiera generado], pass=user123");
            log.info("================================================");
            
        } catch (Exception ex) {
            log.error("Error durante la carga de datos: {}", ex.getMessage(), ex);
            log.error("La aplicación continuará sin los datos de prueba.");
        }
    }

    private void seedUsuarios(int targetUsuarios) {
        long existingNormalUsers = usuarioRepository.countByRole("ROLE_USER") + (long) usuarioRepository.countByRole("USER");
        int missingUsers = Math.max(0, targetUsuarios - (int) existingNormalUsers);

        log.info("Usuarios normales existentes: {}, objetivo: {}, faltantes: {}", existingNormalUsers, targetUsuarios, missingUsers);

        if (missingUsers <= 0) {
            log.info("Ya existen {} usuarios normales. No se agregaron más.", existingNormalUsers);
            return;
        }

        String[] roles = {"ROLE_USER"};
        int createdCount = 0;
        for (int i = 0; i < missingUsers; i++) {
            boolean created = false;
            int tries = 0;

            while (!created && tries < 10) {
                tries++;
                Usuario usuario = new Usuario();
                
                String username = generateUniqueUsername();
                usuario.setUsername(username);
                usuario.setPassword(passwordEncoder.encode("user123"));
                usuario.setRole(roles[random.nextInt(roles.length)]);
                usuario.setNombres(faker.name().firstName());
                usuario.setApellidos(faker.name().lastName());

                String email = faker.internet().emailAddress();
                while (usuarioRepository.existsByEmail(email)) {
                    email = faker.internet().emailAddress();
                }
                usuario.setEmail(email);

                try {
                    usuarioRepository.save(usuario);
                    created = true;
                    createdCount++;
                } catch (Exception ex) {
                    log.warn("⚠️ Intento {} fallido para crear usuario {}: {}", tries, username, ex.getMessage());
                }
            }

            if (!created) {
                log.error("❌ No se pudo crear usuario después de 10 intentos. Se omite este registro y sigue con el siguiente.");
                continue;
            }

            if (createdCount % 250 == 0) {
                log.info("   ✅ {} usuarios creados...", createdCount);
            }
        }

        log.info("✅ {} usuarios creados exitosamente", missingUsers);
    }

    private void seedViajesEjemplo() {
        if (viajeRepository.count() > 0) {
            log.info("✅ Ya existen viajes en la base de datos. No se crearon viajes de ejemplo.");
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.info("⚠️ No hay usuarios para asignar viajes de ejemplo.");
            return;
        }

        List<Ruta> rutas = rutaRepository.findAll();
        if (rutas.isEmpty()) {
            log.info("⚠️ No hay rutas disponibles para asignar viajes de ejemplo.");
            return;
        }

        log.info("✈️ Creando viajes de ejemplo para usuarios existentes...");
        int created = 0;
        String[] estados = {"Completado", "Pendiente", "Cancelado"};

        for (int i = 0; i < 15 && i < usuarios.size(); i++) {
            Usuario usuario = usuarios.get(i);
            Viaje viaje = new Viaje();
            viaje.setUsuario(usuario);
            Ruta ruta = rutas.get(random.nextInt(rutas.size()));
            viaje.setNombreRuta(ruta.getNombre());
            viaje.setFecha(LocalDate.now().minusDays(random.nextInt(30)));
            viaje.setHora(LocalTime.of(random.nextInt(16) + 6, random.nextInt(4) * 15));
            viaje.setEstado(estados[random.nextInt(estados.length)]);
            viajeRepository.save(viaje);
            created++;
        }

        if (created > 0) {
            log.info("✅ {} viajes de ejemplo creados.", created);
        } else {
            log.info("⚠️ No se crearon viajes de ejemplo.");
        }
    }

    private String generateUniqueUsername() {
        String username;
        do {
            String primerNombre = faker.name().firstName().toLowerCase().replaceAll("[^a-z]", "");
            String primerApellido = faker.name().lastName().toLowerCase().replaceAll("[^a-z]", "");
            String baseUsername = primerNombre + "." + primerApellido;

            if (baseUsername.length() > 14) {
                String shortNombre = primerNombre.length() > 3 ? primerNombre.substring(0, 3) : primerNombre;
                String shortApellido = primerApellido.length() > 10 ? primerApellido.substring(0, 10) : primerApellido;
                baseUsername = shortNombre + "." + shortApellido;
            }

            if (baseUsername.length() > 14) {
                baseUsername = baseUsername.substring(0, 14);
            }

            int numero = random.nextInt(9000) + 1000; // 4 dígitos
            username = baseUsername + numero;

            if (username.length() > 20) {
                username = username.substring(0, 20);
            }
        } while (usuarioRepository.existsByUsername(username));
        return username;
    }

    private void normalizarRolesEnBaseDatos() {
        try {
            log.info("🔧 Normalizando roles en la base de datos...");
            
            List<Usuario> allUsers = usuarioRepository.findAll();
            int corrected = 0;

            for (Usuario user : allUsers) {
                String originalRole = user.getRole();
                String normalizedRole = originalRole;

                if (originalRole == null || originalRole.trim().isEmpty()) {
                    normalizedRole = "ROLE_USER";
                } else {
                    originalRole = originalRole.trim().toUpperCase();
                    
                    if ("ADMIN".equals(originalRole)) {
                        normalizedRole = "ROLE_ADMIN";
                    } else if ("USER".equals(originalRole)) {
                        normalizedRole = "ROLE_USER";
                    } else if (!originalRole.startsWith("ROLE_")) {
                        normalizedRole = "ROLE_" + originalRole;
                    } else {
                        normalizedRole = originalRole;
                    }
                }

                if (!normalizedRole.equals(user.getRole())) {
                    user.setRole(normalizedRole);
                    usuarioRepository.save(user);
                    corrected++;
                }
            }

            log.info("✅ {} roles normalizados en la base de datos", corrected);
            log.info("   Roles válidos: ROLE_USER, ROLE_ADMIN");
        } catch (Exception ex) {
            log.error("❌ Error al normalizar roles: {}", ex.getMessage());
        }
    }

}