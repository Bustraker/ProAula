package com.proaula.aula.Entity;

import com.proaula.aula.Controller.RutaController;
import com.proaula.aula.Repository.RutaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback
class RutaPersistenceIntegrationTest {

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private RutaController rutaController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

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
