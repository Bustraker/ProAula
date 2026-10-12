package com.proaula.aula.Service;

import com.proaula.aula.Barrios.Localidades;
import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataSeederService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);
    private static final String OBSERVACION_PROVISIONAL =
        "Carga inicial provisional. Recorrido, ubicación puntual y accesibilidad pendientes de validación.";
    private static final List<RutaInicial> RUTAS_INICIALES = List.of(
        new RutaInicial(
            "RUT-CAMP-SOC-BCG-CG",
            "Campestre - El Socorro - Bocagrande / Castillogrande",
            "El Rodeo",
            "Castillogrande",
            "#1f77b4",
            "Coointracar Ltda. (operador reportado; vigencia pendiente de validar)",
            "Descripción transcrita del documento compartido. Menciona los decretos 2124 y 2127 de octubre de 2025, cuyo texto y vigencia no se verificaron. El retorno se describe en sentido contrario, sujeto al trazado autorizado.",
            List.of("El Rodeo", "El Campestre", "El Socorro", "Blas de Lezo", "Bazurto",
                "Centro", "Bocagrande", "Castillogrande"),
            List.of(
                new PuntoRecorrido("El Rodeo", "El Rodeo"),
                new PuntoRecorrido("El Campestre", "El Campestre"),
                new PuntoRecorrido("Blas de Lezo", "Blas de Lezo"),
                new PuntoRecorrido("Transversal 54", null),
                new PuntoRecorrido("Avenida Crisanto Luque", null),
                new PuntoRecorrido("Avenida Pedro de Heredia", null),
                new PuntoRecorrido("Bazurto", "Bazurto"),
                new PuntoRecorrido("Centro", "Centro"),
                new PuntoRecorrido("Bocagrande", "Bocagrande"),
                new PuntoRecorrido("Castillogrande", "Castillogrande")
            ),
            true,
            false
        ),
        new RutaInicial(
            "RUT-POZ-TORICES",
            "El Pozón - Villagrande - Socorro - Centro - Torices",
            "El Pozón",
            "Torices",
            "#ff7f0e",
            "Coointracar Ltda. (operador reportado; pendiente de confirmar)",
            "Recorrido transcrito del documento compartido; hace falta localizar y verificar el acto administrativo o ficha oficial de esta ruta.",
            List.of("El Pozón", "Villagrande", "San Fernando", "El Socorro", "La Plazuela",
                "Bazurto", "Centro", "Torices"),
            List.of(
                new PuntoRecorrido("El Pozón", "El Pozón"),
                new PuntoRecorrido("Villagrande", "Villagrande"),
                new PuntoRecorrido("San Fernando", "San Fernando"),
                new PuntoRecorrido("El Socorro", "El Socorro"),
                new PuntoRecorrido("La Plazuela", "La Plazuela"),
                new PuntoRecorrido("Bomba El Amparo", null),
                new PuntoRecorrido("Avenida Pedro de Heredia", null),
                new PuntoRecorrido("Bazurto", "Bazurto"),
                new PuntoRecorrido("Centro", "Centro"),
                new PuntoRecorrido("Avenida Santander", null),
                new PuntoRecorrido("Reloj Floral", null),
                new PuntoRecorrido("Torices", "Torices")
            ),
            true,
            true
        ),
        new RutaInicial(
            "RUT-37-SIM-CENTRO",
            "Ruta 37 - Simón Bolívar - San Fernando - Socorro - Centro",
            "Simón Bolívar",
            "Centro",
            "#2ca02c",
            "Transporte público colectivo (operador no confirmado)",
            "Recorrido reportado en el documento compartido. Fuentes distritales citadas: "
                + "https://mejoranormativa.cartagena.gov.co/normativa/memoria-justificativa-proyectos-normativos/memoria-justificativa-ruta-37-311 "
                + "y https://mejoranormativa.cartagena.gov.co/normativa/analisis-impacto-normativo/analisis-impacto-normativo-modificacion-del-recorrido-ruta-37-312. "
                + "Cotejar el trazado y el sentido con los anexos técnicos antes de publicarlo.",
            List.of("Simón Bolívar", "San Fernando", "El Socorro", "Chambacú", "El Cabrero", "Centro"),
            List.of(
                new PuntoRecorrido("Simón Bolívar", "Simón Bolívar"),
                new PuntoRecorrido("Avenida de la Electrificadora", null),
                new PuntoRecorrido("Calle 71 del Socorro", "El Socorro"),
                new PuntoRecorrido("Avenida Pedro de Heredia", null),
                new PuntoRecorrido("Campo de Chambacú", "Chambacú"),
                new PuntoRecorrido("India Catalina", null),
                new PuntoRecorrido("El Cabrero", "El Cabrero"),
                new PuntoRecorrido("Avenida Santander", null),
                new PuntoRecorrido("Parque de la Marina", null),
                new PuntoRecorrido("Centro", "Centro")
            ),
            true,
            true
        ),
        new RutaInicial(
            "RUT-30A-NB-CENTRO",
            "Ruta 30A - Nuevo Bosque - Calamares - Centro",
            "Terminal / Nuevo Bosque",
            "Centro",
            "#9467bd",
            "Cootransurb Ltda. (operador reportado; pendiente de confirmar)",
            "Recorrido resumido en el documento compartido. Índice distrital citado: "
                + "https://mejoranormativa.cartagena.gov.co/normativa/proyectos-especificos-regulacion. "
                + "La publicación sobre modificación de la Ruta 30A de septiembre de 2025 incluye un documento técnico; "
                + "verificar los puntos exactos de inicio y el trazado.",
            List.of("Nuevo Bosque", "Calamares", "Bazurto", "Centro"),
            List.of(
                new PuntoRecorrido("Terminal de Transporte", null),
                new PuntoRecorrido("Avenida 11 de Noviembre", null),
                new PuntoRecorrido("Transversal 54", null),
                new PuntoRecorrido("Avenida Crisanto Luque", null),
                new PuntoRecorrido("Avenida Pedro de Heredia", null),
                new PuntoRecorrido("Bazurto", "Bazurto"),
                new PuntoRecorrido("Puente Román", null),
                new PuntoRecorrido("Media Luna", "Centro"),
                new PuntoRecorrido("Parque Centenario", "Centro"),
                new PuntoRecorrido("Centro", "Centro")
            ),
            true,
            false
        )
    );

    private final BarrioRepository barrioRepository;
    private final BarrioService barrioService;
    private final RutaRepository rutaRepository;
    private final ParadaRepository paradaRepository;
    private final RutaParadaRepository rutaParadaRepository;
    private final AdminCodeService adminCodeService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    @Value("${app.admin.registration-code:}")
    private String adminRegistrationCode;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap-admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    public DataSeederService(BarrioRepository barrioRepository,
                             BarrioService barrioService,
                             RutaRepository rutaRepository,
                             ParadaRepository paradaRepository,
                             RutaParadaRepository rutaParadaRepository,
                             AdminCodeService adminCodeService,
                             UsuarioRepository usuarioRepository,
                             UsuarioService usuarioService) {
        this.barrioRepository = barrioRepository;
        this.barrioService = barrioService;
        this.rutaRepository = rutaRepository;
        this.paradaRepository = paradaRepository;
        this.rutaParadaRepository = rutaParadaRepository;
        this.adminCodeService = adminCodeService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedBarrios();
        seedRutas();
        initializeAdminRegistrationCode();
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

    private void seedRutas() {
        int savedRoutes = 0;
        int savedStops = 0;
        int savedRouteStopRelations = 0;

        for (RutaInicial inicial : RUTAS_INICIALES) {
            Ruta ruta = rutaRepository.findByCodigo(inicial.codigo()).orElse(null);
            if (ruta == null) {
                ruta = new Ruta();
                ruta.setCodigo(inicial.codigo());
                ruta.setNombre(inicial.nombre());
                ruta.setOrigen(inicial.origen());
                ruta.setDestino(inicial.destino());
                ruta.setColorHex(inicial.colorHex());
                ruta.setDescripcion(descripcionProvisional(inicial));
                ruta.setVerificada(false);
                ruta.setBarrios(new ArrayList<>(inicial.barrios()));
                ruta.setBarriosOrdenados(new ArrayList<>(inicial.barrios()));
                ruta = rutaRepository.save(ruta);
                savedRoutes++;
            }

            List<Parada> paradas = paradaRepository.findByRuta_IdOrderByOrdenAsc(ruta.getId());
            if (paradas.isEmpty()) {
                List<Parada> nuevasParadas = new ArrayList<>();
                for (int i = 0; i < inicial.paradas().size(); i++) {
                    PuntoRecorrido punto = inicial.paradas().get(i);
                    Parada parada = new Parada();
                    parada.setNombre(punto.nombre());
                    parada.setUbicacion("Punto de paso referencial: " + punto.nombre());
                    parada.setReferencia(
                        "Ubicación puntual y coordenadas pendientes de validación.");
                    parada.setBarrio(punto.barrio());
                    parada.setOrden(i + 1);
                    parada.setRuta(ruta);
                    parada.setAccesibleMovilidadReducida(false);
                    parada.setObservacionesAccesibilidad(OBSERVACION_PROVISIONAL);
                    nuevasParadas.add(parada);
                }
                paradas = paradaRepository.saveAll(nuevasParadas);
                savedStops += paradas.size();
            }

            savedRouteStopRelations += seedSentido(ruta, paradas, RutaParada.Sentido.IDA, false);
            if (inicial.retornoInverso()) {
                savedRouteStopRelations += seedSentido(ruta, paradas, RutaParada.Sentido.VUELTA, true);
            }
        }

        log.info("Rutas iniciales provisionales: {} nuevas rutas, {} nuevas paradas y {} relaciones de recorrido",
            savedRoutes, savedStops, savedRouteStopRelations);
    }

    private String descripcionProvisional(RutaInicial ruta) {
        return "DATOS PROVISIONALES. No publicar ni usar como recorrido vigente hasta validar. "
            + "Operador reportado: " + ruta.operador() + ". Fuente: " + ruta.fuente()
            + (ruta.retornoSimulado()
                ? " El orden de vuelta se generó invirtiendo la ida y debe validarse."
                : " Horarios y coordenadas exactas no informados.");
    }

    private int seedSentido(Ruta ruta, List<Parada> paradas, RutaParada.Sentido sentido, boolean inverso) {
        if (!rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(ruta.getId(), sentido).isEmpty()) {
            return 0;
        }

        List<RutaParada> relaciones = new ArrayList<>();
        for (int i = 0; i < paradas.size(); i++) {
            int indiceParada = inverso ? paradas.size() - i - 1 : i;
            RutaParada relacion = new RutaParada();
            relacion.setRuta(ruta);
            relacion.setParada(paradas.get(indiceParada));
            relacion.setSentido(sentido);
            relacion.setOrden(i + 1);
            relaciones.add(relacion);
        }
        rutaParadaRepository.saveAll(relaciones);
        return relaciones.size();
    }

    private void initializeAdminRegistrationCode() {
        if (adminRegistrationCode != null && !adminRegistrationCode.isBlank()) {
            adminCodeService.ensureDefaultAdminCodes(List.of(adminRegistrationCode.trim()));
        }
    }

    private void bootstrapAdminIfConfigured() {
        if (!bootstrapAdminEnabled) {
            return;
        }
        if (isBlank(bootstrapAdminUsername) || isBlank(bootstrapAdminEmail)
                || isBlank(bootstrapAdminPassword) || bootstrapAdminPassword.length() < 12
                || bootstrapAdminPassword.length() > 72) {
            throw new IllegalStateException(
                "El aprovisionamiento requiere usuario, correo y contraseña segura (12 a 72 caracteres)");
        }

        String username = bootstrapAdminUsername.trim();
        if (usuarioRepository.existsByUsernameIgnoreCase(username)) {
            if (!usuarioService.isAdminUser(username)) {
                throw new IllegalStateException(
                    "El usuario configurado para el aprovisionamiento existe y no tiene rol de administrador");
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
            .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RutaInicial(
        String codigo,
        String nombre,
        String origen,
        String destino,
        String colorHex,
        String operador,
        String fuente,
        List<String> barrios,
        List<PuntoRecorrido> paradas,
        boolean retornoInverso,
        boolean retornoSimulado
    ) {
    }

    private record PuntoRecorrido(String nombre, String barrio) {
    }
}
