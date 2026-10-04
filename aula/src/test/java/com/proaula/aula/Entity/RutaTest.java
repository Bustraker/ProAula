package com.proaula.aula.Entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RutaTest {

    @Test
    void ordenSoloSeConfirmaCuandoIncluyeLosBarriosRegistrados() {
        Ruta ruta = new Ruta();
        ruta.setBarrios(List.of("Centro", "Manga", "Crespo"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga", "Crespo"));

        assertThat(ruta.isOrdenBarriosConfirmado()).isTrue();
    }

    @Test
    void ordenHeredadoOViejoNoSeConsideraConfirmado() {
        Ruta ruta = new Ruta();
        ruta.setBarrios(List.of("Centro", "Manga", "Crespo"));

        assertThat(ruta.isOrdenBarriosConfirmado()).isFalse();
    }

    @Test
    void ordenNoSeConfirmaSiNoCoincideConLaListaDeBarrios() {
        Ruta ruta = new Ruta();
        ruta.setBarrios(List.of("Centro", "Manga", "Crespo"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga", "Olaya"));

        assertThat(ruta.isOrdenBarriosConfirmado()).isFalse();
    }

    @Test
    void ordenNoSeConfirmaConUnSoloBarrioDistinto() {
        Ruta ruta = new Ruta();
        ruta.setBarrios(List.of("Centro", "Centro"));
        ruta.setBarriosOrdenados(List.of("Centro", "Centro"));

        assertThat(ruta.isOrdenBarriosConfirmado()).isFalse();
    }
}
