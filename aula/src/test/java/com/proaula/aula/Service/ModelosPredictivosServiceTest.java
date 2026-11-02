package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.proaula.aula.dto.modelos.DestinoRequest;
import com.proaula.aula.dto.modelos.HistorialVisitasRequest;
import com.proaula.aula.dto.modelos.OpcionRutaRequest;
import com.proaula.aula.dto.modelos.RecomendacionRutaRequest;
import com.proaula.aula.dto.modelos.RiesgoRetrasoRequest;
import com.proaula.aula.dto.modelos.SugerenciasDestinoRequest;

class ModelosPredictivosServiceTest {
    private MockRestServiceServer server;
    private ModelosPredictivosService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ModelosPredictivosService(
            builder.baseUrl("http://models.test").build());
    }

    @Test
    void enviaRiesgoRetrasoAlServicioYMapeaLaRespuesta() {
        server.expect(requestTo("http://models.test/v1/predictions/delay-risk"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "ruta": "R-02",
                  "diaSemana": "Lunes",
                  "horaSalida": 17,
                  "duracionPlanificadaMin": 48.0,
                  "indiceDemandaPronosticada": 72.0
                }
                """))
            .andRespond(withSuccess("""
                {
                  "riesgo": "ALTO",
                  "probabilidadRetraso": 0.72,
                  "umbral": 0.5,
                  "modelo": "HistGradientBoostingClassifier (clasificación binaria)",
                  "origenDatos": "SINTETICOS_DEMOSTRACION"
                }
                """, MediaType.APPLICATION_JSON));

        var response = service.predecirRiesgo(new RiesgoRetrasoRequest(
            "R-02", "Lunes", 17, 48.0, 72.0));

        assertThat(response.riesgo()).isEqualTo("ALTO");
        assertThat(response.probabilidadRetraso()).isEqualTo(0.72);
        assertThat(response.origenDatos()).isEqualTo("SINTETICOS_DEMOSTRACION");
        server.verify();
    }

    @Test
    void enviaRutasAlServicioYMapeaElOrdenamiento() {
        server.expect(requestTo("http://models.test/v1/recommendations/routes"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "rutaRecomendada": "R-ALT",
                  "tiempoGeneralizadoMin": 40.0,
                  "motivo": "Ahorra tiempo.",
                  "metodo": "Ordenamiento",
                  "origenDatos": "ESTIMACIONES_DEMOSTRACION",
                  "rutasOrdenadas": [{
                    "codigo": "R-ALT",
                    "etaMin": 35.0,
                    "salidasPorHora": 6.0,
                    "demoraClimaMin": 0.0,
                    "congestionPct": 20.0,
                    "tiempoGeneralizadoMin": 40.0
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        var response = service.recomendarRuta(new RecomendacionRutaRequest(
            new OpcionRutaRequest("R-HAB", 55.0, 4.0, 2.0, 50.0),
            List.of(new OpcionRutaRequest("R-ALT", 35.0, 6.0, 0.0, 20.0))));

        assertThat(response.rutaRecomendada()).isEqualTo("R-ALT");
        assertThat(response.rutasOrdenadas()).hasSize(1);
        assertThat(response.rutasOrdenadas().getFirst().codigo()).isEqualTo("R-ALT");
        server.verify();
    }

    @Test
    void enviaHistorialAlServicioYMapeaLasSugerencias() {
        server.expect(requestTo("http://models.test/v1/recommendations/destinations"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "sugerencias": [{
                    "id": "P-01",
                    "nombre": "Parada Manga",
                    "tipo": "parada",
                    "barrio": "Manga",
                    "puntuacion": 0.8,
                    "motivo": "Historial reciente"
                  }],
                  "metodo": "Ranking",
                  "origenDatos": "HISTORIAL_PROPORCIONADO_POR_LA_APLICACION"
                }
                """, MediaType.APPLICATION_JSON));

        var response = service.sugerirDestinos(new SugerenciasDestinoRequest(
            "Manga",
            List.of(new DestinoRequest("P-01", "Parada Manga", "parada", "Manga")),
            Map.of("P-01", new HistorialVisitasRequest(5, 18, 1)),
            5));

        assertThat(response.sugerencias()).hasSize(1);
        assertThat(response.sugerencias().getFirst().id()).isEqualTo("P-01");
        server.verify();
    }

    @Test
    void informaCuandoLaApiPythonNoEstaDisponible() {
        server.expect(requestTo("http://models.test/v1/predictions/delay-risk"))
            .andRespond(request -> {
                throw new IOException("connection refused");
            });

        assertThatThrownBy(() -> service.predecirRiesgo(
            new RiesgoRetrasoRequest("R-02", "Lunes", 17, 48.0, 72.0)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("503 SERVICE_UNAVAILABLE");
        server.verify();
    }

    @Test
    void convierteErroresDeValidacionDelServicioEnSolicitudInvalida() {
        server.expect(requestTo("http://models.test/v1/predictions/delay-risk"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"invalid input\"}"));

        assertThatThrownBy(() -> service.predecirRiesgo(
            new RiesgoRetrasoRequest("R-02", "Lunes", 17, 48.0, 72.0)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST");
        server.verify();
    }
}
