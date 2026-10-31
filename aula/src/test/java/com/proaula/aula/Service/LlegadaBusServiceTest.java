package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.dto.UbicacionBusReporteRequest;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlegadaBusServiceTest {
    private static final Long RUTA_ID = 17L;
    private static final String REPORTANTE = "usuario-prueba";

    @Mock
    private RutaRepository rutaRepository;
    @Mock
    private RutaParadaRepository rutaParadaRepository;

    private MutableClock clock;
    private LlegadaBusService service;
    private Ruta ruta;
    private List<RutaParada> paradas;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-30T18:00:00Z"));
        service = new LlegadaBusService(rutaRepository, rutaParadaRepository, clock);
        ruta = new Ruta();
        ruta.setId(RUTA_ID);
        ruta.setNombre("Centro - Crespo");
        ruta.setVerificada(true);
        ruta.setBarrios(List.of("Centro", "Manga", "Crespo"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga", "Crespo"));
        paradas = List.of(
            relacion(1L, "Centro", 10.420, -75.550, 1),
            relacion(2L, "Manga", 10.430, -75.550, 2),
            relacion(3L, "Crespo", 10.440, -75.550, 3));
        when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
        when(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
            RUTA_ID, RutaParada.Sentido.IDA)).thenReturn(paradas);
    }

    @Test
    void noEstimaLlegadasAntesDeRecibirReportesDePasajeros() {
        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_REPORTES");
        assertThat(resultado.llegadas()).isEmpty();
        assertThat(resultado.mensaje()).contains("no está disponible");
    }

    @Test
    void calculaUnRangoOrientativoSoloTrasObservarMovimientoReciente() {
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.421));
        clock.avanzarSegundos(15);
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.423));
        clock.avanzarSegundos(15);
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.425));

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("ESTIMADO");
        assertThat(resultado.reportantesActivos()).isEqualTo(1);
        assertThat(resultado.llegadas()).hasSize(2);
        assertThat(resultado.llegadas().get(0).parada()).isEqualTo("Manga");
        assertThat(resultado.llegadas().get(0).minutosMinimos()).isPositive();
        assertThat(resultado.llegadas().get(0).minutosMaximos())
            .isGreaterThanOrEqualTo(resultado.llegadas().get(0).minutosMinimos());
        assertThat(resultado.llegadas().get(0).confianza()).isEqualTo("MEDIA");
        assertThat(resultado.toString()).doesNotContain("10.425", REPORTANTE);
    }

    @Test
    void noInventaEtaConUnSoloReporteDeUbicacion() {
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.421));

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_VELOCIDAD");
        assertThat(resultado.reportantesActivos()).isEqualTo(1);
        assertThat(resultado.llegadas()).isEmpty();
    }

    @Test
    void expiraLosReportesDespuesDeNoventaSegundos() {
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.421));
        clock.avanzarSegundos(91);

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_REPORTES");
        assertThat(resultado.reportantesActivos()).isZero();
    }

    @Test
    void rechazaUbicacionesFueraDelRecorrido() {
        assertThatThrownBy(() -> service.reportarUbicacion(
            RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.46)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("demasiado lejos");
    }

    @Test
    void informaCuandoLaRutaNoTieneCoordenadasVerificadasEnSusParadas() {
        when(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
            RUTA_ID, RutaParada.Sentido.IDA)).thenReturn(List.of());

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_COORDENADAS");
        assertThat(resultado.llegadas()).isEmpty();
    }

    @Test
    void noCalculaSobreUnRecorridoQueTieneParadasSinCoordenadas() {
        when(rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(
            RUTA_ID, RutaParada.Sentido.IDA)).thenReturn(List.of(
                paradas.get(0),
                relacion(4L, "Parada sin ubicar", null, null, 2),
                paradas.get(2)));

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_COORDENADAS");
        assertThat(resultado.llegadas()).isEmpty();
    }

    @Test
    void noMuestraLlegadasCuandoElPasajeroAlcanzaLaUltimaParada() {
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.436));
        clock.avanzarSegundos(15);
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.438));
        clock.avanzarSegundos(15);
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.440));

        var resultado = service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA);

        assertThat(resultado.estado()).isEqualTo("SIN_PARADAS_PENDIENTES");
        assertThat(resultado.llegadas()).isEmpty();
    }

    @Test
    void eliminarReportesDelUsuarioDetieneLaDisponibilidadDeSusDatos() {
        service.reportarUbicacion(RUTA_ID, RutaParada.Sentido.IDA, REPORTANTE, ubicacion(10.421));
        service.detenerReportes(REPORTANTE);

        assertThat(service.obtenerLlegadas(RUTA_ID, RutaParada.Sentido.IDA).estado())
            .isEqualTo("SIN_REPORTES");
    }

    private UbicacionBusReporteRequest ubicacion(double latitud) {
        return new UbicacionBusReporteRequest(latitud, -75.550, 10.0);
    }

    private RutaParada relacion(Long paradaId, String nombre, Double latitud, Double longitud, int orden) {
        Parada parada = new Parada();
        parada.setId(paradaId);
        parada.setNombre(nombre);
        parada.setBarrio(nombre);
        parada.setLatitud(latitud);
        parada.setLongitud(longitud);

        RutaParada relacion = new RutaParada();
        relacion.setParada(parada);
        relacion.setRuta(ruta);
        relacion.setSentido(RutaParada.Sentido.IDA);
        relacion.setOrden(orden);
        return relacion;
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instante;

        private MutableClock(Instant inicial) {
            instante = new AtomicReference<>(inicial);
        }

        void avanzarSegundos(long segundos) {
            instante.updateAndGet(actual -> actual.plusSeconds(segundos));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante.get();
        }
    }
}
