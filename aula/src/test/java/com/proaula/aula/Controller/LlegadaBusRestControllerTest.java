package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Service.LlegadaBusService;
import com.proaula.aula.dto.LlegadasRutaResponse;
import com.proaula.aula.dto.UbicacionBusReporteRequest;

@ExtendWith(MockitoExtension.class)
class LlegadaBusRestControllerTest {
    @Mock
    private LlegadaBusService llegadaBusService;

    private LlegadaBusRestController controller;

    @BeforeEach
    void setUp() {
        controller = new LlegadaBusRestController(llegadaBusService);
    }

    @Test
    void permiteConsultarLlegadasDeUnaRutaYUnSentido() {
        LlegadasRutaResponse respuesta = new LlegadasRutaResponse(
            12L, "Centro - Manga", "VUELTA", "SIN_REPORTES", "Sin reportes",
            null, 0, List.of());
        when(llegadaBusService.obtenerLlegadas(12L, RutaParada.Sentido.VUELTA))
            .thenReturn(respuesta);

        var resultado = controller.obtenerLlegadas(12L, RutaParada.Sentido.VUELTA);

        assertThat(resultado.getBody()).isSameAs(respuesta);
        assertThat(resultado.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void asociaLaUbicacionAlaCuentaAutenticadaYDevuelveAccepted() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            "pasajero", "n/a", List.of());
        UbicacionBusReporteRequest ubicacion = new UbicacionBusReporteRequest(10.4, -75.5, 12.0);

        var respuesta = controller.reportarUbicacion(
            12L, RutaParada.Sentido.IDA, ubicacion, authentication);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(respuesta.getBody()).isNull();
        verify(llegadaBusService).reportarUbicacion(
            12L, RutaParada.Sentido.IDA, "pasajero", ubicacion);
    }

    @Test
    void permiteDetenerYEliminarReportesDeLaCuentaAutenticada() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            "pasajero", "n/a", List.of());

        assertThat(controller.detenerReportes(authentication).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        verify(llegadaBusService).detenerReportes("pasajero");
    }
}
