package com.proaula.aula.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
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
import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;

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
    private BCryptPasswordEncoder passwordEncoder;

    private Faker faker = new Faker(new java.util.Locale.Builder().setLanguage("es").setRegion("CO").build()); // Configurado para datos en español de Colombia
    private Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        try {
            // PRIMERO: Guardar TODOS los barrios de Localidades.java en la BD
            log.info("🗺️ Inicializando barrios de Cartagena desde Localidades.java...");
            List<String> allBarrios = Localidades.obtenerTodosLosBarrios();
            Set<String> barriosExistentesNormalizados = barrioRepository.findAll().stream()
                .map(b -> b.getNombre().toLowerCase()
                    .replace("á", "a").replace("é", "e").replace("í", "i")
                    .replace("ó", "o").replace("ú", "u").replace("ñ", "n"))
                .collect(Collectors.toSet());

            // Guardar códigos de administrador en la base de datos
            log.info("🔐 Verificando códigos de administrador en la base de datos...");
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
            log.info("✅ {} barrios guardados en la base de datos", barriosGuardados);
            
            // SEGUNDO: Asignar coordenadas GPS reales
            barrioService.inicializarBarrios();
            
            // Verificar si ya existen datos para evitar duplicados
            boolean existingRoutes = rutaRepository.count() > 0;
            log.info("📊 Datos actuales: {} rutas, {} buses, {} usuarios, {} barrios", 
                rutaRepository.count(), busRepository.count(), usuarioRepository.count(), barrioRepository.count());

            int rutasToCreate = 300;      // 300 rutas
            int busesToCreate = 900;      // 900 buses (3 por ruta en promedio)
            int usuariosToCreate = 3000;  // 3.000 usuarios
            // Total estimado: 300 + 900 + 3.000 + paradas (~900) = ~5.100 registros

            if (existingRoutes) {
                log.info("⚠️ Se detectaron rutas existentes. Se omite la creación de rutas y buses para evitar duplicados.");
            } else {
                log.info("🚀 Iniciando carga masiva de rutas y buses...");
            }

            // Insertar rutas con barrios y paradas
            List<Ruta> rutas = new ArrayList<>();
            if (!existingRoutes) {
                try {
                    log.info("📍 Creando {} rutas...", rutasToCreate);
                    
                    for (int i = 0; i < rutasToCreate; i++) {
                        Ruta ruta = new Ruta();
                        
                        // Nombres más realistas para rutas en Cartagena
                        String[] tiposRuta = {"Ruta", "Línea", "Servicio", "Express"};
                        String[] zonas = {"Centro", "Norte", "Sur", "Occidente", "Oriente", "Universitaria", "Turística"};
                        
                        String nombre = tiposRuta[random.nextInt(tiposRuta.length)] + " " +
                                       zonas[random.nextInt(zonas.length)] + " " +
                                       (i + 1);
                        ruta.setNombre(nombre);
                        
                        // Horarios más realistas (6 AM a 10 PM)
                        int hora = random.nextInt(16) + 6; // 6-22
                        int minuto = random.nextInt(4); // 0, 15, 30, 45
                        ruta.setHoraAproximada(LocalTime.of(hora, minuto * 15));
                        
                        // Asignar 3-6 barrios aleatorios de Localidades
                        List<String> barriosRuta = new ArrayList<>();
                        Set<String> barriosUnicos = new HashSet<>();
                        int numBarrios = random.nextInt(4) + 3; // 3-6 barrios
                        
                        for (int j = 0; j < numBarrios; j++) {
                            String barrio = allBarrios.get(random.nextInt(allBarrios.size()));
                            if (barriosUnicos.add(barrio)) {
                                barriosRuta.add(barrio);
                            }
                        }
                        ruta.setBarrios(barriosRuta);
                        ruta.setParadas(new ArrayList<>());
                        
                        // Agregar 2-5 paradas por ruta
                        int numParadas = random.nextInt(4) + 2; // 2-5 paradas
                        for (int j = 0; j < numParadas; j++) {
                            Parada parada = new Parada();
                            parada.setNombre(faker.address().streetName() + " #" + (j + 1));
                            parada.setUbicacion(faker.address().fullAddress());
                            parada.setReferencia("Cerca a " + faker.commerce().department());
                            parada.setOrden(j + 1);
                            parada.setRuta(ruta);
                            ruta.getParadas().add(parada);
                        }
                        
                        rutas.add(rutaRepository.save(ruta));
                        
                        // Progreso cada 100 rutas
                        if ((i + 1) % 100 == 0) {
                            log.info("   ✅ {} rutas creadas...", (i + 1));
                        }
                    }
                    log.info("✅ {} rutas creadas exitosamente", rutasToCreate);

                    // Agregar rutas predeterminadas para asegurar cobertura
                    log.info("📍 Agregando rutas predeterminadas...");
                    agregarRutasPredeterminadas(rutaRepository, paradaRepository);
                    log.info("✅ Rutas predeterminadas agregadas");

                    // Insertar buses
                    log.info("🚌 Creando {} buses...", busesToCreate);
                    
                    String[] coloresComunes = {"Blanco", "Azul", "Rojo", "Verde", "Amarillo", "Gris", "Negro", "Plateado"};
                    String[] marcasBus = {"Mercedes Benz", "Volvo", "Scania", "MAN", "Iveco", "Ford", "Chevrolet"};
                    
                    for (int i = 0; i < busesToCreate; i++) {
                        Bus bus = new Bus();
                        
                        // Placas más realistas (formato colombiano)
                        String letras = faker.bothify("???").toUpperCase();
                        int numeros = faker.number().numberBetween(100, 999);
                        bus.setPlaca(letras + "-" + String.valueOf(numeros));
                        
                        // Modelo más realista
                        int yearModelo = random.nextInt(15) + 2010; // 2010-2025
                        bus.setModelo(marcasBus[random.nextInt(marcasBus.length)] + " " + yearModelo);
                        
                        bus.setColor(coloresComunes[random.nextInt(coloresComunes.length)]);
                        bus.setConductor(faker.name().fullName());
                        bus.setRuta(rutas.get(random.nextInt(rutas.size())));
                        
                        busRepository.save(bus);
                        
                        // Progreso cada 500 buses
                        if ((i + 1) % 500 == 0) {
                            log.info("   ✅ {} buses creados...", (i + 1));
                        }
                    }
                    log.info("✅ {} buses creados exitosamente", busesToCreate);
                } catch (Exception ex) {
                    log.error("❌ Error al crear rutas y buses: {}", ex.getMessage(), ex);
                    log.warn("Se continúa con la creación de usuarios aunque la carga de rutas/buses haya fallado.");
                }
            } else {
                log.info("✅ Rutas existentes detectadas. No se crearon rutas ni buses nuevos.");
            }

            // Crear usuario administrador por defecto si no existe
            if (!usuarioRepository.existsByUsername("admin")) {
                log.info("👤 Creando administrador...");
                Usuario admin = new Usuario();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ROLE_ADMIN");
                admin.setNombres("Administrador");
                admin.setApellidos("Sistema");
                admin.setEmail("admin@bustraker.edu.co");
                usuarioRepository.save(admin);
                log.info("✅ Administrador creado (user: admin, password: admin123)");
            } else {
                log.info("✅ El administrador 'admin' ya existe. No se creó un nuevo admin.");
            }

            seedUsuarios(usuariosToCreate);

            // NORMALIZAR ROLES en la BD (corregir inconsistencias)
            normalizarRolesEnBaseDatos();

            // Resumen final
            long totalRutas = rutaRepository.count();
            long totalBuses = busRepository.count();
            long totalUsuarios = usuarioRepository.count();
            long totalParadas = paradaRepository.count();
            long totalRegistros = totalRutas + totalBuses + totalUsuarios + totalParadas;
            
            log.info("🎉 ================================================");
            log.info("🎉 CARGA DE DATOS COMPLETADA EXITOSAMENTE");
            log.info("🎉 ================================================");
            log.info("📊 RESUMEN:");
            log.info("   ✅ Rutas: {}", totalRutas);
            log.info("   ✅ Buses: {}", totalBuses);
            log.info("   ✅ Usuarios: {}", totalUsuarios);
            log.info("   ✅ Paradas: {}", totalParadas);
            log.info("   📈 TOTAL REGISTROS: {}", totalRegistros);
            log.info("🎉 ================================================");
            log.info("🔐 CREDENCIALES POR DEFECTO:");
            log.info("   Admin: user=admin, pass=admin123");
            log.info("   Usuario: user=[cualquiera generado], pass=user123");
            log.info("🎉 ================================================");
            
        } catch (Exception ex) {
            log.error("❌ Error durante la carga de datos: {}", ex.getMessage(), ex);
            log.error("La aplicación continuará sin los datos de prueba.");
        }
    }

    private void seedUsuarios(int targetUsuarios) {
        long existingNormalUsers = usuarioRepository.countByRole("ROLE_USER") + (long) usuarioRepository.countByRole("USER");
        int missingUsers = Math.max(0, targetUsuarios - (int) existingNormalUsers);

        log.info("👥 Usuarios normales existentes: {}, objetivo: {}, faltantes: {}", existingNormalUsers, targetUsuarios, missingUsers);

        if (missingUsers <= 0) {
            log.info("✅ Ya existen {} usuarios normales. No se agregaron más.", existingNormalUsers);
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

    private void agregarRutasPredeterminadas(RutaRepository rutaRepository, ParadaRepository paradaRepository) {
        // Rutas que cubren barrios comunes
        List<String[]> rutasData = List.of(
            new String[]{"Centro", "Getsemani", "San Diego", "La Matuna"},
            new String[]{"Bocagrande", "Castillogrande", "El Laguito", "Centro"},
            new String[]{"Pie de la Popa", "La Popa", "Centro", "Getsemani"},
            new String[]{"Terminal", "Centro", "Manga", "Bazurto"},
            new String[]{"Olaya Herrera", "Centro", "La Candelaria", "Pie de la Popa"},
            new String[]{"Nelson Mandela", "Centro", "Getsemani", "San Diego"},
            new String[]{"Villa Olimpica", "Centro", "La Matuna", "El Prado"},
            new String[]{"Bosque", "Centro", "Getsemani", "La Victoria"}
        );

        for (int i = 0; i < rutasData.size(); i++) {
            Ruta ruta = new Ruta();
            ruta.setNombre("Ruta Predeterminada " + (i + 1));
            ruta.setHoraAproximada(LocalTime.of(6 + (i % 4), 0)); // Horas variadas
            ruta.setBarrios(List.of(rutasData.get(i)));
            ruta.setParadas(new ArrayList<>());
            
            // Agregar paradas básicas
            for (int j = 0; j < rutasData.get(i).length; j++) {
                Parada parada = new Parada();
                parada.setNombre("Parada " + rutasData.get(i)[j]);
                parada.setUbicacion("Ubicación en " + rutasData.get(i)[j]);
                parada.setReferencia("Referencia para " + rutasData.get(i)[j]);
                parada.setOrden(j + 1);
                parada.setRuta(ruta);
                ruta.getParadas().add(parada);
            }
            
            rutaRepository.save(ruta);
        }
    }
}