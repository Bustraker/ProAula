package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class BarrioContextoServiceTest {
    private static final String ARCHIVO_CONTEXTO =
        "classpath:static/data/cartografia/barrios-cartografia.geojson";

    private final BarrioContextoService service = new BarrioContextoService(
        new DefaultResourceLoader().getResource(ARCHIVO_CONTEXTO));

    @Test
    void cargaBarriosCartograficosSinInventarDatosDemograficos() {
        var contexto = service.obtenerPorNombre("Alcibia").orElseThrow();

        assertThat(service.disponible()).isTrue();
        assertThat(service.obtenerTodos()).hasSize(208);
        assertThat(contexto.nombreBarrio()).isEqualTo("ALCIBIA");
        assertThat(contexto.poblacion()).isNull();
        assertThat(contexto.areaHectareas()).isNull();
        assertThat(contexto.densidadHabitantesHa()).isNull();
    }

    @Test
    void normalizaNombresConTildesYDeduplicaPoligonosDeUnMismoBarrio() {
        assertThat(service.obtenerPorNombre("España")).isPresent();
        assertThat(service.obtenerPorNombre("Olaya Herrera (Com 5)")).isPresent();
    }

    @Test
    void noInventaDatosParaBarriosAusentesDeLaFuente() {
        assertThat(service.obtenerPorNombre("Barrio sin registro")).isEmpty();
    }
}
