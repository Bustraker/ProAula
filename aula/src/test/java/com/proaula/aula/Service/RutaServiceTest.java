package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;

@ExtendWith(MockitoExtension.class)
class RutaServiceTest {

    @Mock
    private RutaRepository rutaRepository;

    @InjectMocks
    private RutaService rutaService;

    @Test
    void getRutasVerificadasExcluyeRutasNoVerificadasYAntiguas() {
        Ruta verificada = new Ruta();
        verificada.setVerificada(true);
        Ruta noVerificada = new Ruta();
        noVerificada.setVerificada(false);
        Ruta antigua = new Ruta();
        antigua.setVerificada(null);
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of(verificada));

        assertThat(rutaService.getRutasVerificadas()).containsExactly(verificada);
    }
}