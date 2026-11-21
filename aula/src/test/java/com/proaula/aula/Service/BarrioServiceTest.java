package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Repository.BarrioRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BarrioServiceTest {
    private static final String GEOJSON_BARRIOS = "/static/data/cartografia/barrios-cartografia.geojson";

    @Mock
    private BarrioRepository barrioRepository;

    @Captor
    private ArgumentCaptor<Iterable<Barrio>> barriosGuardados;

    @InjectMocks
    private BarrioService barrioService;

    @Test
    void sincronizaSoloNombresCatastralesYEliminaAliasAntiguos() {
        Barrio barrioAntiguo = new Barrio();
        barrioAntiguo.setId(99L);
        barrioAntiguo.setNombre("Saragocilla");
        barrioAntiguo.setLocalidad("Localidad 1");
        when(barrioRepository.findAll()).thenReturn(List.of(barrioAntiguo));

        barrioService.inicializarBarrios();

        verify(barrioRepository).deleteAll(List.of(barrioAntiguo));
        verify(barrioRepository).saveAll(barriosGuardados.capture());
        List<Barrio> guardados = StreamSupport.stream(barriosGuardados.getValue().spliterator(), false).toList();

        assertThat(guardados).hasSize(208)
            .extracting(Barrio::getNombre)
            .contains("ZARAGOCILLA", "NUEVO BOSQUE")
            .doesNotContain("Saragocilla")
            .doesNotHaveDuplicates();
        assertThat(guardados).allSatisfy(barrio ->
            assertThat(barrio.getLocalidad()).isNotBlank());
    }

    @Test
    void noDevuelveCoordenadasInventadasParaNombresDesconocidosYResuelveElAliasConocido() {
        Map<String, double[]> coordenadas = barrioService.obtenerCoordenadas(
            List.of("Zaragocilla", "Saragocilla", "Simón Bolívar", "Barrio inexistente"));

        assertThat(coordenadas.keySet()).containsExactlyInAnyOrder(
            "Zaragocilla", "Saragocilla", "Simón Bolívar");
        assertThat(coordenadas.get("Zaragocilla")).containsExactly(coordenadas.get("Saragocilla"));
        assertThat(barrioService.obtenerNombreCanonico("Saragocilla")).isEqualTo("ZARAGOCILLA");
        assertThat(barrioService.obtenerNombreCanonico("Simón Bolívar"))
            .isEqualTo("URBANIZACION SIMON BOLIVAR");
        assertThat(barrioService.obtenerNombreCanonico("Barrio inexistente")).isEmpty();
    }

    @Test
    void losPuntosRepresentativosQuedanDentroDeLosPoligonosQueLosDefinen() throws IOException {
        JsonNode features;
        try (InputStream entrada = getClass().getResourceAsStream(GEOJSON_BARRIOS)) {
            features = new ObjectMapper().readTree(entrada).path("features");
        }

        Map<String, List<JsonNode>> geometriaPorNombre = new HashMap<>();
        Map<String, String> nombrePorClave = new HashMap<>();
        for (JsonNode feature : features) {
            String nombre = feature.path("properties").path("nombre").asText().trim();
            String clave = BarrioContextoService.normalizarNombre(nombre);
            geometriaPorNombre.computeIfAbsent(clave, ignorado -> new ArrayList<>())
                .add(feature.path("geometry"));
            nombrePorClave.putIfAbsent(clave, nombre);
        }

        for (Map.Entry<String, List<JsonNode>> entrada : geometriaPorNombre.entrySet()) {
            String nombre = nombrePorClave.get(entrada.getKey());
            double[] punto = barrioService.obtenerCoordenadas(List.of(nombre)).get(nombre);

            assertThat(punto).as(nombre).isNotNull().hasSize(2);
            assertThat(entrada.getValue().stream()
                .anyMatch(geometria -> contienePunto(geometria, punto[1], punto[0])))
                .as("El punto de referencia de %s debe pertenecer a su polígono", nombre)
                .isTrue();
        }
    }

    private static boolean contienePunto(JsonNode geometria, double longitud, double latitud) {
        JsonNode coordenadas = geometria.path("coordinates");
        if ("Polygon".equals(geometria.path("type").asText())) {
            return poligonoContienePunto(coordenadas, longitud, latitud);
        }
        if ("MultiPolygon".equals(geometria.path("type").asText())) {
            for (JsonNode poligono : coordenadas) {
                if (poligonoContienePunto(poligono, longitud, latitud)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean poligonoContienePunto(JsonNode anillos, double longitud, double latitud) {
        if (!anillos.isArray() || anillos.isEmpty() || !anilloContienePunto(anillos.get(0), longitud, latitud)) {
            return false;
        }
        for (int indice = 1; indice < anillos.size(); indice++) {
            if (anilloContienePunto(anillos.get(indice), longitud, latitud)) {
                return false;
            }
        }
        return true;
    }

    private static boolean anilloContienePunto(JsonNode anillo, double longitud, double latitud) {
        boolean dentro = false;
        for (int indice = 0, anterior = anillo.size() - 1; indice < anillo.size(); anterior = indice++) {
            JsonNode actual = anillo.get(indice);
            JsonNode previo = anillo.get(anterior);
            double longitudActual = actual.get(0).asDouble();
            double latitudActual = actual.get(1).asDouble();
            double longitudPrevia = previo.get(0).asDouble();
            double latitudPrevia = previo.get(1).asDouble();
            if ((latitudActual > latitud) != (latitudPrevia > latitud)
                    && longitud < (longitudPrevia - longitudActual) * (latitud - latitudActual)
                        / (latitudPrevia - latitudActual) + longitudActual) {
                dentro = !dentro;
            }
        }
        return dentro;
    }
}
