package com.proaula.aula.Service;

import com.proaula.aula.Repository.AsientoRepository;
import com.proaula.aula.Repository.ContactoEmpresaRepository;
import com.proaula.aula.Repository.ContactoMensajeRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Repository.ViajeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "app.synthetic-data.enabled=true",
    "app.synthetic-data.user-count=80",
    "app.synthetic-data.bus-count=16",
    "app.synthetic-data.trip-count=2400",
    "app.synthetic-data.ticket-count=6000",
    "app.synthetic-data.history-days=14",
    "app.synthetic-data.position-count=100",
    "app.synthetic-data.incident-count=20"
})
@ActiveProfiles("validation")
class SyntheticDataSeederServiceTest {

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private ContactoEmpresaRepository contactoEmpresaRepository;

    @Autowired
    private ContactoMensajeRepository contactoMensajeRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private PasajeRepository pasajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyntheticDataSeederService syntheticDataSeederService;

    @Test
    void acceptsSyntheticDemandSourceForEnumsAndWideEnoughTextColumns() {
        assertTrue(SyntheticDataSeederService.supportsSyntheticDemandSource(Map.of(
            "DATA_TYPE", "enum",
            "COLUMN_TYPE", "enum('CONSOLIDADO','HISTORICO','PREDICCION','SINTETICO')"
        )));
        assertFalse(SyntheticDataSeederService.supportsSyntheticDemandSource(Map.of(
            "DATA_TYPE", "enum",
            "COLUMN_TYPE", "enum('CONSOLIDADO','HISTORICO','PREDICCION')"
        )));
        assertTrue(SyntheticDataSeederService.supportsSyntheticDemandSource(Map.of(
            "DATA_TYPE", "varchar",
            "COLUMN_TYPE", "varchar(20)",
            "CHARACTER_MAXIMUM_LENGTH", 20
        )));
        assertFalse(SyntheticDataSeederService.supportsSyntheticDemandSource(Map.of(
            "DATA_TYPE", "varchar",
            "COLUMN_TYPE", "varchar(5)",
            "CHARACTER_MAXIMUM_LENGTH", 5
        )));
    }

    @Test
    void migratesOnlyEnumsThatDoNotContainSyntheticSource() {
        assertTrue(LocalDatabaseBootstrapService.needsSyntheticDemandEnumMigration(
            "enum('CONSOLIDADO','HISTORICO','PREDICCION')"));
        assertTrue(LocalDatabaseBootstrapService.needsSyntheticDemandEnumMigration(
            "enum('CONSOLIDADO','HISTORICO','PREDICCION','MANUAL')"));
        assertEquals("enum('CONSOLIDADO','MANUAL','SINTETICO')",
            LocalDatabaseBootstrapService.appendSyntheticDemandEnumValue(
                "enum('CONSOLIDADO','MANUAL')"));
        assertFalse(LocalDatabaseBootstrapService.needsSyntheticDemandEnumMigration(
            "enum('CONSOLIDADO','HISTORICO','PREDICCION','SINTETICO')"));
        assertFalse(LocalDatabaseBootstrapService.needsSyntheticDemandEnumMigration("varchar(20)"));
    }

    @Test
    void generatesCoherentSyntheticRecordsInH2AndDoesNotDuplicateOnRerun() {
        assertEquals(80, usuarioRepository.count());
        assertEquals(16, asientoRepository.findAll().stream()
            .map(seat -> seat.getBus().getId())
            .distinct()
            .count());
        assertEquals(2400, viajeRepository.count());
        assertEquals(6000, pasajeRepository.count());
        assertTrue(notificacionRepository.count() > 0);
        assertEquals(12, contactoEmpresaRepository.count());
        assertEquals(12, contactoMensajeRepository.count());

        assertTrue(usuarioRepository.findAll().stream()
            .allMatch(user -> user.getUsername().startsWith("demo_")
                && user.getEmail().endsWith("@example.com")
                && user.getProvider().equals("SYNTHETIC")));
        assertTrue(pasajeRepository.findAll().stream()
            .allMatch(ticket -> ticket.getObservacion().contains("DATO SINTETICO")));
        assertTrue(countRows("reporte_ocupacion") > 0);
        assertEquals(100, countRows("reporte_posicion"));
        assertEquals(20, countRows("incidente"));
        assertTrue(countRows("favorito") > 0);
        assertTrue(countRows("demanda_historica") > 0);
        assertTrue(countRows("tarifa") > 0);
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tarifa WHERE estado = TRUE", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT usuario_id FROM favorito
                UNION ALL SELECT usuario_id FROM incidente
                UNION ALL SELECT usuario_id FROM reporte_ocupacion
                UNION ALL SELECT usuario_id FROM reporte_posicion
            ) activity
            JOIN usuario u ON u.id = activity.usuario_id
            WHERE u.provider <> 'SYNTHETIC'
            """, Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM demanda_historica WHERE fuente_datos <> 'SINTETICO'", Integer.class));
        assertFalse(usuarioRepository.findAll().stream()
            .anyMatch(user -> "ROLE_ADMIN".equalsIgnoreCase(user.getRole())));

        syntheticDataSeederService.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(80, usuarioRepository.count());
        assertEquals(2400, viajeRepository.count());
        assertEquals(6000, pasajeRepository.count());
        assertEquals(100, countRows("reporte_posicion"));
        assertEquals(20, countRows("incidente"));
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
