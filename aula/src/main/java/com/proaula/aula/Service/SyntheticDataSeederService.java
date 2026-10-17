package com.proaula.aula.Service;

import com.github.javafaker.Faker;
import com.proaula.aula.Entity.Agencia;
import com.proaula.aula.Entity.Asiento;
import com.proaula.aula.Entity.AsignacionRutaBus;
import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Conductor;
import com.proaula.aula.Entity.ContactoEmpresa;
import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Entity.Horario;
import com.proaula.aula.Entity.Notificacion;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Pasaje;
import com.proaula.aula.Entity.Rol;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Entity.Viaje;
import com.proaula.aula.Repository.AgenciaRepository;
import com.proaula.aula.Repository.AsientoRepository;
import com.proaula.aula.Repository.AsignacionRutaBusRepository;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ConductorRepository;
import com.proaula.aula.Repository.ContactoEmpresaRepository;
import com.proaula.aula.Repository.ContactoMensajeRepository;
import com.proaula.aula.Repository.HorarioRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.RolRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Repository.ViajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.synthetic-data", name = "enabled", havingValue = "true")
public class SyntheticDataSeederService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataSeederService.class);
    private static final String SYNTHETIC_USER_PREFIX = "demo_";
    private static final String SYNTHETIC_NOTE =
        "DATO SINTETICO generado con Faker; no corresponde a una transacción real.";
    private static final List<String> BUS_MAKES =
        List.of("Chevrolet", "Mercedes-Benz", "Volkswagen", "Hino", "Toyota");
    private static final List<String> BUS_MODELS =
        List.of("NPR", "OF-1721", "Volksbus 9.150", "Dutro", "Coaster");
    private static final List<String> BUS_COLORS =
        List.of("Blanco", "Azul", "Verde", "Rojo", "Amarillo");
    private static final List<LocalTime> SCHEDULE_TIMES =
        List.of(LocalTime.of(5, 30), LocalTime.of(7, 0), LocalTime.of(12, 0),
            LocalTime.of(16, 0), LocalTime.of(18, 0));

    private final AgenciaRepository agenciaRepository;
    private final AsientoRepository asientoRepository;
    private final AsignacionRutaBusRepository asignacionRepository;
    private final BarrioRepository barrioRepository;
    private final BusRepository busRepository;
    private final ConductorRepository conductorRepository;
    private final ContactoEmpresaRepository contactoEmpresaRepository;
    private final ContactoMensajeRepository contactoMensajeRepository;
    private final HorarioRepository horarioRepository;
    private final NotificacionRepository notificacionRepository;
    private final ParadaRepository paradaRepository;
    private final PasajeRepository pasajeRepository;
    private final RolRepository rolRepository;
    private final RutaParadaRepository rutaParadaRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ViajeRepository viajeRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.synthetic-data.seed:20261011}")
    private long seed;

    @Value("${app.synthetic-data.user-count:80}")
    private int userCount;

    @Value("${app.synthetic-data.bus-count:16}")
    private int busCount;

    @Value("${app.synthetic-data.trip-count:2400}")
    private int tripCount;

    @Value("${app.synthetic-data.ticket-count:6000}")
    private int ticketCount;

    @Value("${app.synthetic-data.history-days:365}")
    private int historyDays;

    @Value("${app.synthetic-data.position-count:6000}")
    private int positionCount;

    @Value("${app.synthetic-data.incident-count:240}")
    private int incidentCount;

    public SyntheticDataSeederService(
            AgenciaRepository agenciaRepository,
            AsientoRepository asientoRepository,
            AsignacionRutaBusRepository asignacionRepository,
            BarrioRepository barrioRepository,
            BusRepository busRepository,
            ConductorRepository conductorRepository,
            ContactoEmpresaRepository contactoEmpresaRepository,
            ContactoMensajeRepository contactoMensajeRepository,
            HorarioRepository horarioRepository,
            NotificacionRepository notificacionRepository,
            ParadaRepository paradaRepository,
            PasajeRepository pasajeRepository,
            RolRepository rolRepository,
            RutaParadaRepository rutaParadaRepository,
            RutaRepository rutaRepository,
            UsuarioRepository usuarioRepository,
            ViajeRepository viajeRepository,
            BCryptPasswordEncoder passwordEncoder,
            DataSource dataSource,
            JdbcTemplate jdbcTemplate) {
        this.agenciaRepository = agenciaRepository;
        this.asientoRepository = asientoRepository;
        this.asignacionRepository = asignacionRepository;
        this.barrioRepository = barrioRepository;
        this.busRepository = busRepository;
        this.conductorRepository = conductorRepository;
        this.contactoEmpresaRepository = contactoEmpresaRepository;
        this.contactoMensajeRepository = contactoMensajeRepository;
        this.horarioRepository = horarioRepository;
        this.notificacionRepository = notificacionRepository;
        this.paradaRepository = paradaRepository;
        this.pasajeRepository = pasajeRepository;
        this.rolRepository = rolRepository;
        this.rutaParadaRepository = rutaParadaRepository;
        this.rutaRepository = rutaRepository;
        this.usuarioRepository = usuarioRepository;
        this.viajeRepository = viajeRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateSettings();
        boolean h2Memory = validateTargetDatabase();
        if (h2Memory) {
            ensureH2AnalyticsTables();
        } else {
            validateMySqlAnalyticsSchema();
        }

        Random random = new Random(seed);
        Faker faker = new Faker(Locale.forLanguageTag("es"), random);

        List<Ruta> routes = rutaRepository.findAll();
        List<Parada> stops = paradaRepository.findAll();
        List<Usuario> demoUsers = seedUsers(faker);
        List<Usuario> customers = demoUsers.stream()
            .filter(user -> !"ROLE_CONDUCTOR".equalsIgnoreCase(user.getRole()))
            .collect(Collectors.toCollection(ArrayList::new));
        List<Usuario> driverUsers = demoUsers.stream()
            .filter(user -> "ROLE_CONDUCTOR".equalsIgnoreCase(user.getRole()))
            .collect(Collectors.toCollection(ArrayList::new));

        List<Agencia> agencies = seedAgencies(faker);
        List<Bus> buses = seedBuses(faker, random, routes, agencies);
        List<Conductor> conductors = seedDrivers(faker, driverUsers);
        seedSeats(buses);
        seedAssignments(random, buses, routes, conductors);
        seedSchedules(random, buses);
        seedTrips(random, customers, routes);
        seedTickets(random, customers, buses, stops);
        seedNotifications(faker, random, demoUsers);
        seedContactMessages(faker, random);
        List<Usuario> reporters = new ArrayList<>(customers);
        reporters.addAll(driverUsers);
        Map<DemandKey, OccupancySummary> occupancy = seedOccupancyReports(random, reporters, buses);
        seedPositionReports(random, reporters, buses, stops);
        seedIncidents(faker, random, customers, buses, routes);
        seedFavorites(customers, routes, stops);
        seedDemandHistory(random, routes, occupancy);
        seedTariffs(routes);

        long total = barrioRepository.count() + agenciaRepository.count() + rutaRepository.count()
            + paradaRepository.count() + rutaParadaRepository.count() + usuarioRepository.count()
            + busRepository.count() + conductorRepository.count() + asientoRepository.count()
            + asignacionRepository.count() + horarioRepository.count() + viajeRepository.count()
            + pasajeRepository.count() + notificacionRepository.count()
            + contactoEmpresaRepository.count() + contactoMensajeRepository.count()
            + countRows("demanda_historica") + countRows("favorito") + countRows("incidente")
            + countRows("reporte_ocupacion") + countRows("reporte_posicion") + countRows("tarifa");
        log.info("Carga Faker finalizada. Registros operativos actuales: {}. "
            + "Los usuarios sintéticos usan el prefijo '{}' y no se crearon códigos de administración.",
            total, SYNTHETIC_USER_PREFIX);
    }

    private List<Usuario> seedUsers(Faker faker) {
        List<Usuario> existingUsers = usuarioRepository.findAll();
        List<Usuario> existingDemoUsers = existingUsers.stream()
            .filter(user -> user.getUsername() != null && user.getUsername().startsWith(SYNTHETIC_USER_PREFIX)
                && "SYNTHETIC".equalsIgnoreCase(user.getProvider()))
            .collect(Collectors.toCollection(ArrayList::new));
        if (existingDemoUsers.size() >= userCount) {
            return existingDemoUsers;
        }
        if (userCount == 0) {
            log.warn("No se generaron cuentas sintéticas: app.synthetic-data.user-count está en cero.");
            return existingDemoUsers;
        }

        Rol userRole = ensureRole("ROLE_USER", "Cuenta sintética de usuario para pruebas");
        Rol driverRole = ensureRole("ROLE_CONDUCTOR", "Cuenta sintética de conductor para pruebas");
        int driverCount = userCount < 2 ? 0 : Math.min(20, Math.max(1, userCount / 10));
        long existingDriverCount = existingDemoUsers.stream()
            .filter(user -> "ROLE_CONDUCTOR".equalsIgnoreCase(user.getRole()))
            .count();
        Set<String> usernames = existingUsers.stream()
            .map(user -> user.getUsername())
            .filter(username -> username != null)
            .collect(Collectors.toSet());
        Set<String> emails = existingUsers.stream()
            .map(user -> user.getEmail())
            .filter(email -> email != null)
            .collect(Collectors.toSet());
        List<Usuario> users = new ArrayList<>(userCount - existingDemoUsers.size());
        for (int i = 1; existingDemoUsers.size() + users.size() < userCount && i <= 9999; i++) {
            Usuario user = new Usuario();
            String username = String.format(Locale.ROOT, "demo_%04d", i);
            if (usernames.contains(username) || emails.contains(username + "@example.com")) {
                continue;
            }
            long generatedDrivers = users.stream()
                .filter(created -> "ROLE_CONDUCTOR".equalsIgnoreCase(created.getRole()))
                .count();
            boolean isDriver = existingDriverCount + generatedDrivers < driverCount;
            Rol role = isDriver ? driverRole : userRole;
            user.setUsername(username);
            user.setEmail(username + "@example.com");
            user.setPassword(passwordEncoder.encode(faker.internet().password(12, 16, true, true)));
            user.setNombres(limit(faker.name().firstName(), 100));
            user.setApellidos(limit(faker.name().lastName(), 100));
            user.setProvider("SYNTHETIC");
            user.setRole(role.getNombre());
            user.setRoles(new HashSet<>(Set.of(role)));
            users.add(user);
            usernames.add(username);
            emails.add(user.getEmail());
        }
        if (existingDemoUsers.size() + users.size() < userCount) {
            throw new IllegalStateException("No se pudieron generar todas las cuentas sintéticas por conflictos de username/email.");
        }
        existingDemoUsers.addAll(usuarioRepository.saveAll(users));
        return existingDemoUsers;
    }

    private Rol ensureRole(String name, String description) {
        return rolRepository.findByNombreIgnoreCase(name).orElseGet(() -> {
            Rol role = new Rol();
            role.setNombre(name);
            role.setDescripcion(description);
            return rolRepository.save(role);
        });
    }

    private List<Agencia> seedAgencies(Faker faker) {
        if (agenciaRepository.count() != 0) {
            return List.of();
        }

        List<Agencia> agencies = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Agencia agency = new Agencia();
            agency.setNombre(limit(faker.company().name() + " (sintética)", 100));
            agency.setDireccion(limit("Cartagena, Bolívar, " + faker.address().streetAddress(), 255));
            agency.setTelefono(faker.phoneNumber().cellPhone().replaceAll("[^0-9+ -]", ""));
            agency.setCorreo("agencia" + i + "@example.com");
            agencies.add(agency);
        }
        return agenciaRepository.saveAll(agencies);
    }

    private List<Bus> seedBuses(Faker faker, Random random, List<Ruta> routes, List<Agencia> agencies) {
        if (busRepository.count() != 0) {
            return busRepository.findAll();
        }
        if (routes.isEmpty()) {
            log.warn("No se generaron buses: no hay rutas disponibles.");
            return List.of();
        }
        if (busCount == 0) {
            log.warn("No se generaron buses: app.synthetic-data.bus-count está en cero.");
            return List.of();
        }

        List<Bus> buses = new ArrayList<>();
        for (int i = 0; i < busCount; i++) {
            Bus bus = new Bus();
            bus.setPlaca(generatedPlate(i));
            bus.setNumeroInterno(String.format(Locale.ROOT, "SYN-BUS-%04d", i + 1));
            bus.setMarca(BUS_MAKES.get(random.nextInt(BUS_MAKES.size())));
            bus.setModelo(BUS_MODELS.get(random.nextInt(BUS_MODELS.size())));
            bus.setColor(BUS_COLORS.get(random.nextInt(BUS_COLORS.size())));
            bus.setConductor(faker.name().fullName());
            bus.setCapacidadPasajeros(List.of(30, 35, 40, 45).get(random.nextInt(4)));
            bus.setAnio(2012 + random.nextInt(13));
            bus.setEstado(random.nextInt(100) < 92 ? Bus.Estado.ACTIVO : Bus.Estado.MANTENIMIENTO);
            bus.setAccesibleMovilidadReducida(random.nextInt(100) < 12);
            bus.setNivelOcupacionActual(randomOccupancy(random));
            bus.setUltimaActualizacionOcupacion(LocalDateTime.now().minusMinutes(random.nextInt(60)));
            bus.setRuta(routes.get(i % routes.size()));
            if (!agencies.isEmpty()) {
                bus.setAgencia(agencies.get(i % agencies.size()));
            }
            buses.add(bus);
        }
        return busRepository.saveAll(buses);
    }

    private List<Conductor> seedDrivers(Faker faker, List<Usuario> demoUsers) {
        if (conductorRepository.count() != 0) {
            return conductorRepository.findAll().stream()
                .filter(driver -> driver.getUsuario() != null
                    && driver.getUsuario().getUsername() != null
                    && driver.getUsuario().getUsername().startsWith(SYNTHETIC_USER_PREFIX))
                .collect(Collectors.toList());
        }
        if (demoUsers.isEmpty()) {
            log.warn("No se generaron conductores: no hay cuentas sintéticas disponibles.");
            return List.of();
        }

        List<Conductor> drivers = new ArrayList<>();
        int count = Math.min(demoUsers.size(), Math.max(1, busCount));
        for (int i = 0; i < count; i++) {
            Conductor driver = new Conductor();
            driver.setNumeroIdentificacion("CC-" + faker.number().digits(10));
            driver.setLicenciaConducir("C2-" + faker.number().digits(8));
            driver.setEstado(Conductor.Estado.ACTIVO);
            driver.setUsuario(demoUsers.get(i));
            drivers.add(driver);
        }
        return conductorRepository.saveAll(drivers);
    }

    private void seedSeats(List<Bus> buses) {
        if (asientoRepository.count() != 0) {
            return;
        }

        List<Asiento> seats = new ArrayList<>();
        for (Bus bus : buses) {
            if (bus.getNumeroInterno() == null || !bus.getNumeroInterno().startsWith("SYN-BUS-")) {
                continue;
            }
            for (int number = 1; number <= bus.getCapacidadPasajeros(); number++) {
                Asiento seat = new Asiento();
                seat.setNumero(number);
                seat.setTipo(number <= 4 ? "PREFERENCIAL" : "REGULAR");
                seat.setEstado("DISPONIBLE");
                seat.setBus(bus);
                seats.add(seat);
            }
        }
        if (!seats.isEmpty()) {
            asientoRepository.saveAll(seats);
        }
    }

    private void seedAssignments(Random random, List<Bus> buses, List<Ruta> routes,
            List<Conductor> conductors) {
        if (asignacionRepository.count() != 0) {
            return;
        }
        if (routes.isEmpty() || conductors.isEmpty()) {
            log.warn("No se generaron asignaciones: se requieren buses, rutas y conductores sintéticos.");
            return;
        }

        List<AsignacionRutaBus> assignments = new ArrayList<>();
        List<Bus> syntheticBuses = buses.stream()
            .filter(bus -> bus.getNumeroInterno() != null && bus.getNumeroInterno().startsWith("SYN-BUS-"))
            .collect(Collectors.toList());
        for (int i = 0; i < Math.min(syntheticBuses.size(), conductors.size()); i++) {
            Bus bus = syntheticBuses.get(i);
            Ruta route = bus.getRuta() == null ? routes.get(i % routes.size()) : bus.getRuta();
            AsignacionRutaBus assignment = new AsignacionRutaBus();
            assignment.setBus(bus);
            assignment.setRuta(route);
            assignment.setConductor(conductors.get(i));
            assignment.setTurno(AsignacionRutaBus.Turno.values()[random.nextInt(
                AsignacionRutaBus.Turno.values().length)]);
            assignment.setEstado(AsignacionRutaBus.Estado.ACTIVA);
            assignment.setFechaAsignacion(LocalDateTime.now().minusDays(random.nextInt(365)));
            assignments.add(assignment);
        }
        if (!assignments.isEmpty()) {
            asignacionRepository.saveAll(assignments);
        }
    }

    private void seedSchedules(Random random, List<Bus> buses) {
        if (horarioRepository.count() != 0) {
            return;
        }

        Map<Long, Ruta> routesById = new LinkedHashMap<>();
        for (Bus bus : buses) {
            if (bus.getNumeroInterno() != null && bus.getNumeroInterno().startsWith("SYN-BUS-")
                    && bus.getRuta() != null && bus.getRuta().getId() != null) {
                routesById.putIfAbsent(bus.getRuta().getId(), bus.getRuta());
            }
        }
        List<Ruta> routes = new ArrayList<>(routesById.values());
        if (routes.isEmpty()) {
            log.warn("No se generaron horarios: no hay rutas asociadas a buses sintéticos.");
            return;
        }

        List<Horario> schedules = new ArrayList<>();
        for (Ruta route : routes) {
            for (RutaParada.Sentido direction : RutaParada.Sentido.values()) {
                if (rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(route.getId(), direction).isEmpty()) {
                    continue;
                }
                for (int i = 0; i < SCHEDULE_TIMES.size(); i++) {
                    Horario schedule = new Horario();
                    schedule.setRuta(route);
                    schedule.setSentido(direction == RutaParada.Sentido.IDA
                        ? Horario.Sentido.IDA : Horario.Sentido.VUELTA);
                    LocalTime departure = SCHEDULE_TIMES.get(i);
                    schedule.setHoraSalida(departure);
                    schedule.setHoraLlegada(departure.plusMinutes(
                        route.getTiempoEstimadoMinutos() == null ? 45 : route.getTiempoEstimadoMinutos()));
                    schedule.setFrecuenciaMinutos(10 + random.nextInt(16));
                    schedule.setDiasSemana("LUNES,MARTES,MIERCOLES,JUEVES,VIERNES");
                    schedule.setTipoDia(Horario.TipoDia.HABIL);
                    schedule.setEstado(Horario.Estado.INACTIVO);
                    schedules.add(schedule);
                }
            }
        }
        if (!schedules.isEmpty()) {
            horarioRepository.saveAll(schedules);
        }
    }

    private void seedTrips(Random random, List<Usuario> customers, List<Ruta> routes) {
        if (viajeRepository.count() != 0) {
            return;
        }
        if (customers.isEmpty() || routes.isEmpty()) {
            log.warn("No se generaron viajes: se requieren usuarios sintéticos y rutas disponibles.");
            return;
        }

        List<Viaje> trips = new ArrayList<>(tripCount);
        for (int i = 0; i < tripCount; i++) {
            Ruta route = routes.get(random.nextInt(routes.size()));
            LocalDate travelDate = LocalDate.now().minusDays(random.nextInt(366));
            Viaje trip = new Viaje();
            trip.setNombreRuta(route.getNombre());
            trip.setFecha(travelDate);
            trip.setHora(randomCommuteTime(random));
            trip.setEstado(random.nextInt(100) < 92 ? "Completado" : "Cancelado");
            trip.setUsuario(customers.get(random.nextInt(customers.size())));
            trip.setRuta(route);
            trips.add(trip);
        }
        viajeRepository.saveAll(trips);
    }

    private void seedTickets(Random random, List<Usuario> customers, List<Bus> buses, List<Parada> stops) {
        if (pasajeRepository.count() != 0) {
            return;
        }
        if (customers.isEmpty()) {
            log.warn("No se generaron pasajes: no hay usuarios sintéticos disponibles.");
            return;
        }

        List<Bus> syntheticBuses = buses.stream()
            .filter(bus -> bus.getNumeroInterno() != null && bus.getNumeroInterno().startsWith("SYN-BUS-"))
            .filter(bus -> bus.getRuta() != null && bus.getEstado() == Bus.Estado.ACTIVO)
            .collect(Collectors.toList());
        if (syntheticBuses.isEmpty()) {
            log.warn("No se generaron pasajes: no hay buses sintéticos activos asociados a una ruta.");
            return;
        }

        List<Asiento> seats = asientoRepository.findAll();
        List<Pasaje> tickets = new ArrayList<>(ticketCount);
        for (int i = 0; i < ticketCount; i++) {
            Bus bus = syntheticBuses.get(random.nextInt(syntheticBuses.size()));
            List<Parada> routeStops = stops.stream()
                .filter(stop -> stop.getRuta() != null && stop.getRuta().getId().equals(bus.getRuta().getId()))
                .sorted((first, second) -> Integer.compare(
                    first.getOrden() == null ? 0 : first.getOrden(),
                    second.getOrden() == null ? 0 : second.getOrden()))
                .collect(Collectors.toList());
            if (routeStops.size() < 2) {
                continue;
            }

            int originIndex = random.nextInt(routeStops.size() - 1);
            int destinationIndex = originIndex + 1 + random.nextInt(routeStops.size() - originIndex - 1);
            Pasaje ticket = new Pasaje();
            ticket.setFechaViaje(LocalDateTime.of(
                LocalDate.now().minusDays(random.nextInt(366)), randomCommuteTime(random)));
            int status = random.nextInt(100);
            ticket.setEstado(status < 6 ? "CANCELADO" : status < 15 ? "RESERVADO" : "COMPLETADO");
            ticket.setObservacion(SYNTHETIC_NOTE);
            ticket.setUsuario(customers.get(random.nextInt(customers.size())));
            ticket.setRuta(bus.getRuta());
            ticket.setBus(bus);
            ticket.setParadaOrigen(routeStops.get(originIndex));
            ticket.setParadaDestino(routeStops.get(destinationIndex));
            List<Asiento> busSeats = seats.stream()
                .filter(seat -> seat.getBus() != null && seat.getBus().getId().equals(bus.getId()))
                .collect(Collectors.toList());
            if (!busSeats.isEmpty()) {
                ticket.setAsiento(busSeats.get(random.nextInt(busSeats.size())));
            }
            tickets.add(ticket);
        }
        if (!tickets.isEmpty()) {
            pasajeRepository.saveAll(tickets);
        }
    }

    private void seedNotifications(Faker faker, Random random, List<Usuario> users) {
        if (notificacionRepository.count() != 0) {
            return;
        }
        if (users.isEmpty()) {
            log.warn("No se generaron notificaciones: no hay usuarios sintéticos disponibles.");
            return;
        }

        List<Notificacion> notifications = new ArrayList<>();
        for (int i = 0; i < Math.min(users.size() * 2, 120); i++) {
            Notificacion notification = new Notificacion();
            notification.setUsuario(users.get(random.nextInt(users.size())));
            notification.setTitulo(limit(faker.lorem().sentence(4), 150));
            notification.setMensaje(SYNTHETIC_NOTE);
            notification.setTipo(i % 3 == 0 ? "INFORMATIVA" : "VIAJE");
            boolean read = random.nextInt(100) < 65;
            notification.setLeido(read);
            notification.setFechaLeido(read ? LocalDateTime.now().minusDays(random.nextInt(90)) : null);
            notifications.add(notification);
        }
        notificacionRepository.saveAll(notifications);
    }

    private void seedContactMessages(Faker faker, Random random) {
        if (contactoEmpresaRepository.count() == 0) {
            List<ContactoEmpresa> messages = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                ContactoEmpresa message = new ContactoEmpresa();
                message.setNombre(limit(faker.name().fullName(), 100));
                message.setTelefono(faker.phoneNumber().cellPhone().replaceAll("[^0-9+ -]", ""));
                message.setCorreo("empresa" + i + "@example.com");
                message.setMensaje(SYNTHETIC_NOTE + " " + faker.lorem().sentence(8));
                message.setLeido(random.nextBoolean());
                messages.add(message);
            }
            contactoEmpresaRepository.saveAll(messages);
        }

        if (contactoMensajeRepository.count() == 0) {
            List<ContactoMensaje> messages = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                ContactoMensaje message = new ContactoMensaje();
                message.setNombre(limit(faker.name().firstName(), 100));
                message.setApellido(limit(faker.name().lastName(), 100));
                message.setTelefono(faker.phoneNumber().cellPhone());
                message.setEmail("contacto" + i + "@example.com");
                message.setMensaje(SYNTHETIC_NOTE + " " + faker.lorem().sentence(8));
                messages.add(message);
            }
            contactoMensajeRepository.saveAll(messages);
        }
    }

    private void ensureH2AnalyticsTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS reporte_ocupacion (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                bus_id BIGINT NOT NULL,
                usuario_id BIGINT NOT NULL,
                nivel VARCHAR(10) NOT NULL,
                cantidad_pasajeros INTEGER,
                comentario VARCHAR(255),
                reportado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (bus_id) REFERENCES bus(id),
                FOREIGN KEY (usuario_id) REFERENCES usuario(id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS reporte_posicion (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                bus_id BIGINT NOT NULL,
                usuario_id BIGINT NOT NULL,
                latitud DECIMAL(10,7) NOT NULL,
                longitud DECIMAL(10,7) NOT NULL,
                precision_mt DECIMAL(6,2),
                velocidad_kmh DECIMAL(5,2),
                nivel_confianza VARCHAR(10) NOT NULL DEFAULT 'MEDIA',
                reportado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                valido BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (bus_id) REFERENCES bus(id),
                FOREIGN KEY (usuario_id) REFERENCES usuario(id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS incidente (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                ruta_id BIGINT,
                bus_id BIGINT,
                tipo VARCHAR(30) NOT NULL,
                titulo VARCHAR(150) NOT NULL,
                descripcion CLOB NOT NULL,
                estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
                fecha_reporte TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                fecha_resolucion TIMESTAMP,
                comentario_admin CLOB,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (usuario_id) REFERENCES usuario(id),
                FOREIGN KEY (ruta_id) REFERENCES ruta(id),
                FOREIGN KEY (bus_id) REFERENCES bus(id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS favorito (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                tipo VARCHAR(10) NOT NULL,
                ruta_id BIGINT,
                parada_id BIGINT,
                etiqueta VARCHAR(100),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (usuario_id, ruta_id),
                UNIQUE (usuario_id, parada_id),
                FOREIGN KEY (usuario_id) REFERENCES usuario(id),
                FOREIGN KEY (ruta_id) REFERENCES ruta(id),
                FOREIGN KEY (parada_id) REFERENCES parada(id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS demanda_historica (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                ruta_id BIGINT NOT NULL,
                fecha DATE NOT NULL,
                dia_semana VARCHAR(12) NOT NULL,
                franja_horaria VARCHAR(24) NOT NULL,
                total_consultas INTEGER NOT NULL DEFAULT 0,
                reportes_ocupacion_baja INTEGER DEFAULT 0,
                reportes_ocupacion_media INTEGER DEFAULT 0,
                reportes_ocupacion_alta INTEGER DEFAULT 0,
                promedio_ocupacion DECIMAL(5,2),
                nivel_demanda_estimado VARCHAR(10),
                datos_suficientes BOOLEAN NOT NULL DEFAULT FALSE,
                fuente_datos VARCHAR(20) NOT NULL DEFAULT 'CONSOLIDADO',
                actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (ruta_id, fecha, franja_horaria),
                FOREIGN KEY (ruta_id) REFERENCES ruta(id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS tarifa (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                ruta_id BIGINT NOT NULL,
                tipo_usuario VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
                valor DECIMAL(10,2) NOT NULL,
                vigencia_desde TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                vigencia_hasta TIMESTAMP,
                estado BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (ruta_id) REFERENCES ruta(id)
            )
            """);
    }

    private void validateMySqlAnalyticsSchema() {
        Map<String, Set<String>> requiredColumns = Map.of(
            "demanda_historica", Set.of("ruta_id", "fecha", "dia_semana", "franja_horaria",
                "total_consultas", "reportes_ocupacion_baja", "reportes_ocupacion_media",
                "reportes_ocupacion_alta", "promedio_ocupacion", "nivel_demanda_estimado",
                "datos_suficientes", "fuente_datos"),
            "favorito", Set.of("usuario_id", "tipo", "ruta_id", "parada_id", "etiqueta", "created_at"),
            "incidente", Set.of("usuario_id", "ruta_id", "bus_id", "tipo", "titulo",
                "descripcion", "estado", "fecha_reporte", "fecha_resolucion", "comentario_admin"),
            "reporte_ocupacion", Set.of("bus_id", "usuario_id", "nivel", "cantidad_pasajeros",
                "comentario", "reportado_en"),
            "reporte_posicion", Set.of("bus_id", "usuario_id", "latitud", "longitud",
                "precision_mt", "velocidad_kmh", "nivel_confianza", "reportado_en", "valido"),
            "tarifa", Set.of("ruta_id", "tipo_usuario", "valor", "vigencia_desde",
                "vigencia_hasta", "estado"));
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (Map.Entry<String, Set<String>> table : requiredColumns.entrySet()) {
                Set<String> actualColumns = new java.util.HashSet<>();
                try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table.getKey(), null)) {
                    while (columns.next()) {
                        actualColumns.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                if (!actualColumns.containsAll(table.getValue())) {
                    Set<String> missingColumns = new java.util.HashSet<>(table.getValue());
                    missingColumns.removeAll(actualColumns);
                    throw new IllegalStateException("El esquema MySQL de " + table.getKey()
                        + " no tiene las columnas requeridas: " + missingColumns);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo validar el esquema analítico de MySQL.", exception);
        }

        List<Map<String, Object>> sourceColumns = jdbcTemplate.queryForList(
            "SELECT DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            "demanda_historica", "fuente_datos");
        if (sourceColumns.isEmpty()
                || !supportsSyntheticDemandSource(sourceColumns.get(0))) {
            throw new IllegalStateException("demanda_historica.fuente_datos debe aceptar el valor SINTETICO.");
        }
    }

    static boolean supportsSyntheticDemandSource(Map<String, Object> column) {
        String dataType = String.valueOf(metadataValue(column, "DATA_TYPE")).toLowerCase(Locale.ROOT);
        String columnType = String.valueOf(metadataValue(column, "COLUMN_TYPE")).toLowerCase(Locale.ROOT);
        if (dataType.equals("enum")) {
            return columnType.contains("'sintetico'");
        }
        if (Set.of("tinytext", "text", "mediumtext", "longtext").contains(dataType)) {
            return true;
        }
        if (!Set.of("char", "varchar").contains(dataType)) {
            return false;
        }

        Object maximumLength = metadataValue(column, "CHARACTER_MAXIMUM_LENGTH");
        return maximumLength instanceof Number length && length.intValue() >= "SINTETICO".length();
    }

    private static Object metadataValue(Map<String, Object> metadata, String columnName) {
        return metadata.entrySet().stream()
            .filter(entry -> columnName.equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private Map<DemandKey, OccupancySummary> seedOccupancyReports(
            Random random, List<Usuario> reporters, List<Bus> buses) {
        Map<DemandKey, OccupancySummary> summaryByDemand = new HashMap<>();
        if (countRows("reporte_ocupacion") != 0) {
            return summaryByDemand;
        }
        List<Bus> syntheticBuses = buses.stream().filter(this::isSyntheticBus)
            .filter(bus -> bus.getRuta() != null && bus.getId() != null)
            .collect(Collectors.toList());
        if (reporters.isEmpty() || syntheticBuses.isEmpty()) {
            log.warn("No se generaron reportes de ocupación: se requieren usuarios y buses sintéticos.");
            return summaryByDemand;
        }

        List<Object[]> rows = new ArrayList<>();
        int reportCount = Math.min(5000, Math.max(100, syntheticBuses.size() * 40));
        for (int i = 0; i < reportCount; i++) {
            Bus bus = syntheticBuses.get(random.nextInt(syntheticBuses.size()));
            Usuario reporter = reporters.get(random.nextInt(reporters.size()));
            LocalDateTime reportedAt = LocalDate.now().minusDays(random.nextInt(historyDays))
                .atTime(randomCommuteTime(random));
            String level = randomOccupancy(random).name();
            int capacity = bus.getCapacidadPasajeros() == null ? 40 : bus.getCapacidadPasajeros();
            int lowerPercent = "BAJA".equals(level) ? 15 : "MEDIA".equals(level) ? 40 : 75;
            int upperPercent = "BAJA".equals(level) ? 35 : "MEDIA".equals(level) ? 70 : 100;
            int passengerCount = Math.max(1, capacity * (lowerPercent
                + random.nextInt(upperPercent - lowerPercent + 1)) / 100);
            rows.add(new Object[] {bus.getId(), reporter.getId(), level, passengerCount,
                SYNTHETIC_NOTE, Timestamp.valueOf(reportedAt), Timestamp.valueOf(reportedAt)});

            String timeBand = timeBand(reportedAt.toLocalTime());
            DemandKey key = new DemandKey(bus.getRuta().getId(), reportedAt.toLocalDate(), timeBand);
            OccupancySummary summary = summaryByDemand.computeIfAbsent(key, ignored -> new OccupancySummary());
            summary.add(level, passengerCount * 100.0 / capacity);
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO reporte_ocupacion "
                + "(bus_id, usuario_id, nivel, cantidad_pasajeros, comentario, reportado_en, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
        return summaryByDemand;
    }

    private void seedPositionReports(Random random, List<Usuario> reporters, List<Bus> buses,
            List<Parada> stops) {
        if (positionCount == 0 || countRows("reporte_posicion") != 0) {
            return;
        }
        List<Bus> syntheticBuses = buses.stream().filter(this::isSyntheticBus)
            .filter(bus -> bus.getRuta() != null && bus.getId() != null)
            .collect(Collectors.toList());
        if (reporters.isEmpty() || syntheticBuses.isEmpty()) {
            log.warn("No se generaron reportes de posición: se requieren usuarios y buses sintéticos.");
            return;
        }
        Map<Long, List<Parada>> stopsByRoute = new HashMap<>();
        for (Parada stop : stops) {
            if (stop.getRuta() != null && stop.getRuta().getId() != null) {
                stopsByRoute.computeIfAbsent(stop.getRuta().getId(), ignored -> new ArrayList<>()).add(stop);
            }
        }

        List<Object[]> rows = new ArrayList<>(positionCount);
        for (int i = 0; i < positionCount; i++) {
            Bus bus = syntheticBuses.get(random.nextInt(syntheticBuses.size()));
            Usuario reporter = reporters.get(random.nextInt(reporters.size()));
            LocalDateTime reportedAt = LocalDate.now().minusDays(random.nextInt(historyDays))
                .atTime(randomCommuteTime(random));
            List<Parada> routeStops = stopsByRoute.getOrDefault(bus.getRuta().getId(), List.of());
            Parada referenceStop = routeStops.isEmpty() ? null : routeStops.get(random.nextInt(routeStops.size()));
            double baseLatitude = referenceStop != null && referenceStop.getLatitud() != null
                ? referenceStop.getLatitud() : 10.399;
            double baseLongitude = referenceStop != null && referenceStop.getLongitud() != null
                ? referenceStop.getLongitud() : -75.514;
            double latitude = baseLatitude + (random.nextDouble() - 0.5) * 0.002;
            double longitude = baseLongitude + (random.nextDouble() - 0.5) * 0.002;
            rows.add(new Object[] {bus.getId(), reporter.getId(), decimal(latitude, 7), decimal(longitude, 7),
                decimal(5 + random.nextDouble() * 20, 2), decimal(random.nextDouble() * 55, 2),
                random.nextInt(100) < 80 ? "ALTA" : "MEDIA", Timestamp.valueOf(reportedAt),
                random.nextInt(100) < 97, Timestamp.valueOf(reportedAt)});
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO reporte_posicion "
                + "(bus_id, usuario_id, latitud, longitud, precision_mt, velocidad_kmh, nivel_confianza, "
                + "reportado_en, valido, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void seedIncidents(Faker faker, Random random, List<Usuario> customers,
            List<Bus> buses, List<Ruta> routes) {
        if (incidentCount == 0 || countRows("incidente") != 0) {
            return;
        }
        if (customers.isEmpty()) {
            log.warn("No se generaron incidentes: no hay usuarios sintéticos disponibles.");
            return;
        }
        List<Bus> syntheticBuses = buses.stream().filter(this::isSyntheticBus)
            .collect(Collectors.toList());
        String[] types = {"RETRASO", "ACCIDENTE", "FALLA_SERVICIO", "RUTA_DESVIADA", "OTRO"};
        String[] statuses = {"PENDIENTE", "EN_ATENCION", "RESUELTO", "DESCARTADO"};
        String[] titles = {"Retraso en el servicio", "Novedad operativa", "Desvío temporal",
            "Condición de seguridad", "Falla reportada"};
        List<Object[]> rows = new ArrayList<>(incidentCount);
        for (int i = 0; i < incidentCount; i++) {
            Usuario reporter = customers.get(random.nextInt(customers.size()));
            Ruta route = routes.isEmpty() ? null : routes.get(random.nextInt(routes.size()));
            Bus bus = syntheticBuses.isEmpty() || random.nextInt(100) < 25
                ? null : syntheticBuses.get(random.nextInt(syntheticBuses.size()));
            LocalDateTime reportedAt = LocalDate.now().minusDays(random.nextInt(historyDays))
                .atTime(randomCommuteTime(random));
            String status = statuses[random.nextInt(statuses.length)];
            LocalDateTime resolvedAt = "RESUELTO".equals(status) || "DESCARTADO".equals(status)
                ? reportedAt.plusHours(1 + random.nextInt(8)) : null;
            String description = SYNTHETIC_NOTE + " "
                + limit(faker.lorem().sentence(10), 350);
            String adminComment = resolvedAt == null ? null : SYNTHETIC_NOTE;
            rows.add(new Object[] {reporter.getId(), route == null ? null : route.getId(),
                bus == null ? null : bus.getId(), types[random.nextInt(types.length)],
                titles[random.nextInt(titles.length)], description, status, Timestamp.valueOf(reportedAt),
                resolvedAt == null ? null : Timestamp.valueOf(resolvedAt), adminComment,
                Timestamp.valueOf(reportedAt), Timestamp.valueOf(resolvedAt == null ? reportedAt : resolvedAt)});
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO incidente (usuario_id, ruta_id, bus_id, tipo, titulo, descripcion, estado, "
                + "fecha_reporte, fecha_resolucion, comentario_admin, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void seedFavorites(List<Usuario> customers, List<Ruta> routes, List<Parada> stops) {
        if (countRows("favorito") != 0) {
            return;
        }
        if (customers.isEmpty() || (routes.isEmpty() && stops.isEmpty())) {
            log.warn("No se generaron favoritos: se requieren usuarios sintéticos y rutas o paradas.");
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            Usuario customer = customers.get(i);
            Ruta route = routes.isEmpty() ? null : routes.get(i % routes.size());
            if (route != null) {
                rows.add(new Object[] {customer.getId(), "RUTA", route.getId(), null,
                    "Ruta frecuente", Timestamp.valueOf(LocalDate.now().minusDays(i % 90).atStartOfDay())});
                List<Parada> routeStops = stops.stream()
                    .filter(stop -> stop.getRuta() != null && stop.getRuta().getId().equals(route.getId()))
                    .collect(Collectors.toList());
                if (!routeStops.isEmpty()) {
                    Parada stop = routeStops.get(i % routeStops.size());
                    rows.add(new Object[] {customer.getId(), "PARADA", null, stop.getId(),
                        "Parada habitual", Timestamp.valueOf(LocalDate.now().minusDays(i % 90).atStartOfDay())});
                }
            } else if (!stops.isEmpty()) {
                Parada stop = stops.get(i % stops.size());
                rows.add(new Object[] {customer.getId(), "PARADA", null, stop.getId(),
                    "Parada habitual", Timestamp.valueOf(LocalDate.now().minusDays(i % 90).atStartOfDay())});
            }
        }
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO favorito (usuario_id, tipo, ruta_id, parada_id, etiqueta, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", rows);
        }
    }

    private void seedDemandHistory(Random random, List<Ruta> routes,
            Map<DemandKey, OccupancySummary> occupancy) {
        if (countRows("demanda_historica") != 0) {
            return;
        }
        if (routes.isEmpty()) {
            log.warn("No se generó demanda histórica sintética: no hay rutas disponibles.");
            return;
        }
        String[] timeBands = {"MADRUGADA", "PICO_MAÑANERO", "MEDIODIA", "PICO_TARDE", "NOCHE"};
        List<Object[]> rows = new ArrayList<>(Math.min(50000, routes.size() * historyDays * timeBands.length));
        for (Ruta route : routes) {
            for (int dayOffset = 0; dayOffset < historyDays; dayOffset++) {
                LocalDate date = LocalDate.now().minusDays(dayOffset);
                for (String timeBand : timeBands) {
                    DemandKey key = new DemandKey(route.getId(), date, timeBand);
                    OccupancySummary summary = occupancy.get(key);
                    int baseline = "PICO_MAÑANERO".equals(timeBand) || "PICO_TARDE".equals(timeBand) ? 45 : 22;
                    if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        baseline = baseline * 3 / 4;
                    } else if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        baseline = baseline * 9 / 10;
                    }
                    int totalQueries = summary == null ? Math.max(4, baseline + random.nextInt(31) - 12)
                        : summary.totalReports;
                    int low = summary == null ? totalQueries * 25 / 100 : summary.lowReports;
                    int medium = summary == null ? totalQueries * 50 / 100 : summary.mediumReports;
                    int high = summary == null ? totalQueries - low - medium : summary.highReports;
                    double average = summary == null
                        ? 20 + random.nextDouble() * 65 : summary.averageOccupancyPercent();
                    String demandLevel = average < 35 ? "BAJA" : average < 70 ? "MEDIA" : "ALTA";
                    rows.add(new Object[] {route.getId(), Date.valueOf(date), dayOfWeek(date), timeBand,
                        totalQueries, low, medium, high, decimal(average, 2), demandLevel,
                        totalQueries >= 5, "SINTETICO"});
                }
            }
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO demanda_historica (ruta_id, fecha, dia_semana, franja_horaria, total_consultas, "
                + "reportes_ocupacion_baja, reportes_ocupacion_media, reportes_ocupacion_alta, "
                + "promedio_ocupacion, nivel_demanda_estimado, datos_suficientes, fuente_datos) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void seedTariffs(List<Ruta> routes) {
        if (countRows("tarifa") != 0) {
            return;
        }
        if (routes.isEmpty()) {
            log.warn("No se generaron tarifas: no hay rutas disponibles.");
            return;
        }
        String[] passengerTypes = {"GENERAL", "ESTUDIANTE", "ADULTO_MAYOR"};
        BigDecimal[] amounts = {new BigDecimal("3000.00"), new BigDecimal("1800.00"),
            new BigDecimal("1500.00")};
        List<Object[]> rows = new ArrayList<>(routes.size() * passengerTypes.length);
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            Ruta route = routes.get(routeIndex);
            LocalDateTime startsAt = LocalDateTime.now().plusDays(30 + routeIndex);
            LocalDateTime endsAt = startsAt.plusDays(365);
            for (int typeIndex = 0; typeIndex < passengerTypes.length; typeIndex++) {
                rows.add(new Object[] {route.getId(), passengerTypes[typeIndex], amounts[typeIndex],
                    Timestamp.valueOf(startsAt), Timestamp.valueOf(endsAt), false,
                    Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now())});
            }
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO tarifa (ruta_id, tipo_usuario, valor, vigencia_desde, vigencia_hasta, estado, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private long countRows(String table) {
        if (!Set.of("demanda_historica", "favorito", "incidente", "reporte_ocupacion",
                "reporte_posicion", "tarifa").contains(table)) {
            throw new IllegalArgumentException("Nombre de tabla no permitido para conteo: " + table);
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private boolean isSyntheticBus(Bus bus) {
        return bus.getNumeroInterno() != null && bus.getNumeroInterno().startsWith("SYN-BUS-");
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    private String dayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private String timeBand(LocalTime time) {
        int hour = time.getHour();
        if (hour < 5) {
            return "MADRUGADA";
        }
        if (hour >= 6 && hour < 9) {
            return "PICO_MAÑANERO";
        }
        if (hour >= 11 && hour < 14) {
            return "MEDIODIA";
        }
        if (hour >= 16 && hour < 19) {
            return "PICO_TARDE";
        }
        return hour >= 19 ? "NOCHE" : "MADRUGADA";
    }

    private void validateSettings() {
        if (userCount < 0 || userCount > 1000 || busCount < 0 || busCount > 100
                || tripCount < 0 || tripCount > 10000 || ticketCount < 0 || ticketCount > 50000
                || historyDays < 1 || historyDays > 730
                || positionCount < 0 || positionCount > 10000
                || incidentCount < 0 || incidentCount > 1000) {
            throw new IllegalArgumentException(
                "Límites app.synthetic-data: usuarios 0-1000, buses 0-100, viajes 0-10000, pasajes 0-50000, "
                    + "history-days 1-730, position-count 0-10000 e incident-count 0-1000.");
        }
    }

    private boolean validateTargetDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            boolean h2Memory = url.startsWith("jdbc:h2:mem:");
            boolean localMysql = url.matches(
                "(?i)^jdbc:mysql://(localhost|127\\.0\\.0\\.1|\\[::1\\])(?::\\d+)?/.*");
            if (!h2Memory && !localMysql) {
                throw new IllegalStateException(
                    "La carga Faker sólo está permitida en H2 en memoria o en MySQL de la máquina local.");
            }
            return h2Memory;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo comprobar el destino de la carga Faker.", exception);
        }
    }

    private record DemandKey(Long routeId, LocalDate date, String timeBand) {
    }

    private static final class OccupancySummary {
        private int totalReports;
        private int lowReports;
        private int mediumReports;
        private int highReports;
        private double occupancyPercentTotal;

        private void add(String level, double occupancyPercent) {
            totalReports++;
            occupancyPercentTotal += occupancyPercent;
            switch (level) {
                case "BAJA" -> lowReports++;
                case "MEDIA" -> mediumReports++;
                case "ALTA" -> highReports++;
                default -> throw new IllegalArgumentException("Nivel de ocupación desconocido: " + level);
            }
        }

        private double averageOccupancyPercent() {
            return totalReports == 0 ? 0 : occupancyPercentTotal / totalReports;
        }
    }

    private Bus.NivelOcupacion randomOccupancy(Random random) {
        int value = random.nextInt(100);
        if (value < 25) {
            return Bus.NivelOcupacion.BAJA;
        }
        if (value < 70) {
            return Bus.NivelOcupacion.MEDIA;
        }
        return Bus.NivelOcupacion.ALTA;
    }

    private LocalTime randomCommuteTime(Random random) {
        int hour;
        int period = random.nextInt(100);
        if (period < 40) {
            hour = 6 + random.nextInt(3);
        } else if (period < 75) {
            hour = 16 + random.nextInt(4);
        } else {
            hour = 5 + random.nextInt(17);
        }
        int minute = random.nextInt(4) * 15 + random.nextInt(5);
        return LocalTime.of(hour, Math.min(minute, 59));
    }

    private String generatedPlate(int index) {
        int letters = index / 999;
        int serial = index % 999 + 1;
        char first = (char) ('A' + (letters / (26 * 26)) % 26);
        char second = (char) ('A' + (letters / 26) % 26);
        char third = (char) ('A' + letters % 26);
        return String.format(Locale.ROOT, "%c%c%c%03d", first, second, third, serial);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
