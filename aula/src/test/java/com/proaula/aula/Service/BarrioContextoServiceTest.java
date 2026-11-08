package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class BarrioContextoServiceTest {
    private static final String ARCHIVO_CONTEXTO =
        "classpath:data/Barrios_Oficiales_Distrito_de_Cartagena_de_Indias_20261107.xml";

    private final BarrioContextoService service = new BarrioContextoService(
        new DefaultResourceLoader().getResource(ARCHIVO_CONTEXTO));

    @Test
    void cargaContextoOficialYCalculaDensidadAproximada() {
        var contexto = service.obtenerPorNombre("Alcibia").orElseThrow();

        assertThat(service.disponible()).isTrue();
        assertThat(contexto.poblacion()).isEqualTo(4192);
        assertThat(contexto.areaHectareas()).hasToString("24");
        assertThat(contexto.hogares()).isEqualTo(878);
        assertThat(contexto.viviendas()).isEqualTo(869);
        assertThat(contexto.densidadHabitantesHa()).hasToString("174.7");
    }

    @Test
    void relacionaNombresConCaracteresCorruptosDelArchivoConLosBarriosDeLaAplicacion() {
        assertThat(service.obtenerPorNombre("España")).isPresent();
        assertThat(service.obtenerPorNombre("Olaya St. Rafael Núñez")).isPresent();
    }

    @Test
    void noInventaDatosParaBarriosAusentesDeLaFuente() {
        assertThat(service.obtenerPorNombre("Barrio sin registro")).isEmpty();
    }
}
