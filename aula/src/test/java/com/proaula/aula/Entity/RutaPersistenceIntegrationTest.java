package com.proaula.aula.Entity;

import com.proaula.aula.Controller.RutaController;
import com.proaula.aula.Repository.BarrioRepository;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.DataSeederService;
import com.proaula.aula.Service.RutaService;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.exception.AulaException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Rollback
class RutaPersistenceIntegrationTest {

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private ParadaRepository paradaRepository;

    @Autowired
    private BarrioRepository barrioRepository;

    @Autowired
    private RutaParadaRepository rutaParadaRepository;

    @Autowired
    private DataSeederService dataSeederService;

    @Autowired
    private RutaController rutaController;

    @Autowired
    private BusService busService;

    @Autowired
    private RutaService rutaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void generatedRouteStopAndBusCodesSatisfyLegacyDatabaseConstraints() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Prueba de códigos generados");
        Ruta rutaGuardada = rutaRepository.saveAndFlush(ruta);

        Bus bus = new Bus();
        bus.setPlaca("T" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        bus.setModelo("Bus de prueba");
        bus.setRuta(rutaGuardada);
        Bus busGuardado = busRepository.saveAndFlush(bus);

        Parada parada = new Parada();
        parada.setNombre("Parada de prueba");
        parada.setRuta(rutaGuardada);
        Parada paradaGuardada = paradaRepository.saveAndFlush(parada);

        assertThat(rutaGuardada.getCodigo()).startsWith("RUT-").hasSize(20);
        assertThat(busGuardado.getNumeroInterno()).startsWith("BUS-").hasSize(20);
        assertThat(paradaGuardada.getCodigo()).startsWith("PAR-").hasSize(20);
    }

    @Test
    void initialCartagenaRoutesAndStopsArePersistedWithoutDuplicates() {
        Ruta ruta = rutaRepository.findByCodigo("RUT-CAMP-SOC-BCG-CG").orElseThrow();
        List<Parada> paradas = paradaRepository.findByRuta_IdOrderByOrdenAsc(ruta.getId());
        long totalRutas = rutaRepository.count();
        long totalParadas = paradaRepository.count();
        long totalRelaciones = rutaParadaRepository.count();

        assertThat(ruta.getNombre()).contains("El Socorro", "Castillogrande");
        assertThat(ruta.getVerificada()).isFalse();
        assertThat(ruta.getDescripcion()).contains("DATOS PROVISIONALES", "Coointracar Ltda.");
        assertThat(paradas).hasSize(10);
        assertThat(paradas).extracting(Parada::getOrden)
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
            ruta.getId(), RutaParada.Sentido.IDA))
            .extracting(relacion -> relacion.getParada().getNombre())
            .first().isEqualTo("El Rodeo");
        assertThat(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
            ruta.getId(), RutaParada.Sentido.VUELTA))
            .extracting(relacion -> relacion.getParada().getNombre())
            .first().isEqualTo("Castillogrande");

        for (String codigo : List.of("RUT-POZ-TORICES", "RUT-37-SIM-CENTRO", "RUT-30A-NB-CENTRO")) {
            Ruta otraRuta = rutaRepository.findByCodigo(codigo).orElseThrow();
            assertThat(paradaRepository.findByRuta_IdOrderByOrdenAsc(otraRuta.getId())).isNotEmpty();
            assertThat(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
                otraRuta.getId(), RutaParada.Sentido.IDA)).isNotEmpty();
            assertThat(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
                otraRuta.getId(), RutaParada.Sentido.VUELTA)).isNotEmpty();
        }
        assertThat(barrioRepository.findAll()).extracting(Barrio::getNombre)
            .contains("El Rodeo", "Villagrande", "La Plazuela", "Torices", "Calamares", "Simón Bolívar");

        dataSeederService.run();

        assertThat(rutaRepository.count()).isEqualTo(totalRutas);
        assertThat(paradaRepository.count()).isEqualTo(totalParadas);
        assertThat(rutaParadaRepository.count()).isEqualTo(totalRelaciones).isEqualTo(84);
        assertThat(paradaRepository.findByRuta_IdOrderByOrdenAsc(ruta.getId())).hasSize(10);
    }

    @Test
    void newDiagramEntitiesPersistTheirRelationshipsAndLegacyEnumValues() {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Usuario usuario = new Usuario();
        usuario.setUsername("test" + suffix);
        usuario.setPassword("PasswordTest1!");
        usuario.setNombres("Usuario");
        usuario.setApellidos("Integración");
        usuario.setEmail("test" + suffix + "@example.com");
        usuario.setRole("ROLE_TEST");
        entityManager.persist(usuario);

        Agencia agencia = new Agencia();
        agencia.setNombre("Agencia de prueba " + suffix);
        entityManager.persist(agencia);

        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta de prueba " + suffix);
        entityManager.persist(ruta);

        Bus bus = new Bus();
        bus.setPlaca("T" + suffix.substring(0, 8));
        bus.setModelo("Bus de integración");
        bus.setRuta(ruta);
        bus.setAgencia(agencia);
        entityManager.persist(bus);

        Parada origen = new Parada();
        origen.setNombre("Origen " + suffix);
        origen.setRuta(ruta);
        entityManager.persist(origen);

        Parada destino = new Parada();
        destino.setNombre("Destino " + suffix);
        destino.setRuta(ruta);
        entityManager.persist(destino);

        Asiento asiento = new Asiento();
        asiento.setNumero(1);
        asiento.setBus(bus);
        entityManager.persist(asiento);

        Horario horario = new Horario();
        horario.setRuta(ruta);
        horario.setSentido(Horario.Sentido.IDA);
        horario.setHoraSalida(LocalTime.of(6, 30));
        horario.setDiasSemana("LUNES,MARTES,MIERCOLES");
        entityManager.persist(horario);

        RutaParada rutaParada = new RutaParada();
        rutaParada.setRuta(ruta);
        rutaParada.setParada(origen);
        rutaParada.setOrden(1);
        entityManager.persist(rutaParada);

        Conductor conductor = new Conductor();
        conductor.setUsuario(usuario);
        conductor.setNumeroIdentificacion("ID-" + suffix);
        conductor.setLicenciaConducir("LIC-" + suffix);
        entityManager.persist(conductor);

        Rol rol = new Rol();
        rol.setNombre("ROLE_TEST_" + suffix);
        entityManager.persist(rol);
        usuario.getRoles().add(rol);

        AsignacionRutaBus asignacion = new AsignacionRutaBus();
        asignacion.setRuta(ruta);
        asignacion.setBus(bus);
        asignacion.setConductor(conductor);
        asignacion.setTurno(AsignacionRutaBus.Turno.MANANA);
        entityManager.persist(asignacion);

        Pasaje pasaje = new Pasaje();
        pasaje.setUsuario(usuario);
        pasaje.setRuta(ruta);
        pasaje.setBus(bus);
        pasaje.setParadaOrigen(origen);
        pasaje.setParadaDestino(destino);
        pasaje.setAsiento(asiento);
        pasaje.setFechaViaje(LocalDateTime.now().plusDays(1));
        entityManager.persist(pasaje);

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTitulo("Prueba");
        notificacion.setMensaje("Notificación de integración");
        notificacion.setTipo("SISTEMA");
        entityManager.persist(notificacion);

        ContactoEmpresa contacto = new ContactoEmpresa();
        contacto.setNombre("Contacto de prueba");
        contacto.setCorreo("contacto" + suffix + "@example.com");
        contacto.setMensaje("Mensaje de integración");
        entityManager.persist(contacto);

        entityManager.flush();
        Long horarioId = horario.getId();
        Long rutaParadaId = rutaParada.getId();
        Long asignacionId = asignacion.getId();
        Long pasajeId = pasaje.getId();
        Long notificacionId = notificacion.getId();
        Long contactoId = contacto.getId();
        Long usuarioId = usuario.getId();
        entityManager.clear();

        assertThatThrownBy(() -> usuarioService.deleteById(usuarioId))
            .isInstanceOf(AulaException.class)
            .hasMessageContaining("tiene viajes, pasajes");
        assertThatThrownBy(() -> busService.deleteBus(bus.getId()))
            .isInstanceOf(AulaException.class)
            .hasMessageContaining("tiene asientos, asignaciones o pasajes");
        assertThatThrownBy(() -> rutaService.deleteRuta(ruta.getId()))
            .isInstanceOf(AulaException.class)
            .hasMessageContaining("tiene buses, paradas, horarios");

        assertThat(entityManager.find(Horario.class, horarioId).getDiasSemana())
            .isEqualTo("LUNES,MARTES,MIERCOLES");
        assertThat(entityManager.find(RutaParada.class, rutaParadaId).getOrden()).isEqualTo(1);
        assertThat(entityManager.find(AsignacionRutaBus.class, asignacionId).getTurno())
            .isEqualTo(AsignacionRutaBus.Turno.MANANA);
        assertThat(entityManager.find(Pasaje.class, pasajeId).getAsiento().getNumero()).isEqualTo(1);
        assertThat(entityManager.find(Notificacion.class, notificacionId).getTitulo()).isEqualTo("Prueba");
        assertThat(entityManager.find(ContactoEmpresa.class, contactoId).getCorreo())
            .isEqualTo("contacto" + suffix + "@example.com");
        assertThat(entityManager.find(Usuario.class, usuarioId).getRoles())
            .extracting(Rol::getNombre)
            .containsExactly("ROLE_TEST_" + suffix);
    }

    @Test
    void adminCreateAndEditPersistRouteOrderWithoutInferringLegacyOrder() {
        Ruta nueva = new Ruta();
        nueva.setNombre("Prueba persistencia orden - nueva");
        rutaController.agregarRuta(nueva, "Centro, Manga, Crespo");

        Long idNueva = Objects.requireNonNull(entityManager.createQuery(
                "select ruta.id from Ruta ruta where ruta.nombre = :nombre order by ruta.id desc",
                Long.class
            )
            .setParameter("nombre", nueva.getNombre())
            .setMaxResults(1)
            .getSingleResult());
        entityManager.flush();
        List<String> filasOrdenadas = jdbcTemplate.queryForList(
            "select barrio from ruta_barrios_ordenados where ruta_id = ? order by posicion",
            String.class,
            idNueva
        );
        entityManager.clear();
        Ruta creada = rutaRepository.findById(idNueva).orElseThrow();
        assertThat(filasOrdenadas).containsExactly("Centro", "Manga", "Crespo");
        assertThat(creada.getBarrios()).containsExactly("Centro", "Manga", "Crespo");
        assertThat(creada.getBarriosOrdenados()).containsExactly("Centro", "Manga", "Crespo");
        assertThat(creada.getCodigo()).startsWith("RUT-").hasSize(20);
        assertThat(creada.isOrdenBarriosConfirmado()).isTrue();

        Ruta heredada = new Ruta();
        heredada.setNombre("Prueba persistencia orden - heredada");
        heredada.setBarrios(List.of("Centro", "Manga", "Crespo"));
        Ruta rutaHeredada = rutaRepository.saveAndFlush(heredada);
        Long idHeredada = Objects.requireNonNull(rutaHeredada.getId());

        Ruta formularioEdicion = new Ruta();
        formularioEdicion.setVerificada(true);
        rutaController.guardarEdicionRuta(idNueva, formularioEdicion, "Crespo, Manga, Centro");
        entityManager.flush();
        entityManager.clear();
        Ruta recargada = rutaRepository.findById(idNueva).orElseThrow();
        Ruta recargadaHeredada = rutaRepository.findById(idHeredada).orElseThrow();

        assertThat(recargada.getBarrios()).containsExactly("Crespo", "Manga", "Centro");
        assertThat(recargada.getBarriosOrdenados()).containsExactly("Crespo", "Manga", "Centro");
        assertThat(recargada.isOrdenBarriosConfirmado()).isTrue();
        assertThat(recargada.getVerificada()).isTrue();
        assertThat(recargadaHeredada.getBarriosOrdenados()).isEmpty();
        assertThat(recargadaHeredada.isOrdenBarriosConfirmado()).isFalse();
    }
}
