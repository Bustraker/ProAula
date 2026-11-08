package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.dto.modelos.PrioridadCoberturaResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class ModeloPrioridadCoberturaServiceTest {
    private final BarrioContextoService barrioContextoService = new BarrioContextoService(
        new DefaultResourceLoader().getResource(
            "classpath:data/Barrios_Oficiales_Distrito_de_Cartagena_de_Indias_20261107.xml"));
    private final RutaRepository rutaRepository = mock(RutaRepository.class);
    private final ModeloPrioridadCoberturaService service =
        new ModeloPrioridadCoberturaService(barrioContextoService, rutaRepository);

    @Test
    void barriosSinRutasQuedanPrioritariosYExplicanCadaComponente() {
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of());

        var resultado = service.analizarCobertura(false);
        var alcibia = barrio(resultado.barriosPrioritarios(), "ALCIBIA");

        assertThat(resultado.nombreModelo()).containsIgnoringCase("prioridad potencial");
        assertThat(resultado.totalBarriosAnalizados()).isEqualTo(187);
        assertThat(resultado.barriosPrioridadAlta()).isEqualTo(187);
        assertThat(resultado.poblacionEnBarriosSinRutaVerificada()).isPositive();
        assertThat(alcibia.rutasActivasVerificadas()).isZero();
        assertThat(alcibia.indicePrioridad()).isEqualTo(63.9);
        assertThat(alcibia.nivelPrioridad()).isEqualTo("ALTA");
        assertThat(alcibia.factores()).anyMatch(factor -> factor.contains("Sin ruta activa"));
        assertThat(resultado.advertencia()).contains("no tiene año de medición identificado");
    }

    @Test
    void coberturaSoloCuentaRutasActivasVerificadasConOrdenDeBarriosConfirmado() {
        Ruta rutaActiva = ruta("ALCIBIA", Ruta.Estado.ACTIVA);
        Ruta rutaNoVerificada = ruta("ALCIBIA", Ruta.Estado.ACTIVA);
        rutaNoVerificada.setVerificada(false);
        Ruta rutaSuspendida = ruta("ALCIBIA", Ruta.Estado.SUSPENDIDA);
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(
            List.of(rutaActiva, rutaSuspendida, rutaNoVerificada));

        var resultado = service.analizarCobertura(false);
        var alcibia = barrio(resultado.barriosPrioritarios(), "ALCIBIA");

        assertThat(alcibia.rutasActivasVerificadas()).isEqualTo(1);
        assertThat(alcibia.paradasActivasRegistradas()).isEqualTo(1);
        assertThat(alcibia.paradasActivasGeorreferenciadas()).isZero();
        assertThat(alcibia.aporteFaltaRuta()).isEqualTo(20.0);
        assertThat(alcibia.aporteFaltaParadasGeorreferenciadas()).isEqualTo(10.0);
    }

    @Test
    void ubicacionesInvalidasNoSeConsideranParadasGeorreferenciadas() {
        Ruta ruta = ruta("ALCIBIA", Ruta.Estado.ACTIVA);
        Parada parada = ruta.getParadas().getFirst();
        parada.setLatitud(200.0);
        parada.setLongitud(-75.5);
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of(ruta));

        var alcibia = barrio(service.analizarCobertura(false).barriosPrioritarios(), "ALCIBIA");

        assertThat(alcibia.paradasActivasGeorreferenciadas()).isZero();
        assertThat(alcibia.factores()).anyMatch(factor -> factor.contains("sin coordenadas"));
    }

    @Test
    void clasificaBarriosConMultiplesRutasYParadasUbicadasSinPenalizarLaCobertura() {
        Ruta primeraRuta = ruta("ALCIBIA", Ruta.Estado.ACTIVA);
        primeraRuta.getParadas().getFirst().setLatitud(10.410239);
        primeraRuta.getParadas().getFirst().setLongitud(-75.515723);
        Ruta segundaRuta = ruta("ALCIBIA", Ruta.Estado.ACTIVA);
        segundaRuta.getParadas().getFirst().setId(2L);
        segundaRuta.getParadas().getFirst().setLatitud(10.411);
        segundaRuta.getParadas().getFirst().setLongitud(-75.516);
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of(primeraRuta, segundaRuta));

        var alcibia = barrio(service.analizarCobertura(false).barriosPrioritarios(), "ALCIBIA");

        assertThat(alcibia.rutasActivasVerificadas()).isEqualTo(2);
        assertThat(alcibia.paradasActivasRegistradas()).isEqualTo(2);
        assertThat(alcibia.paradasActivasGeorreferenciadas()).isEqualTo(2);
        assertThat(alcibia.indicePrioridad()).isEqualTo(3.9);
        assertThat(alcibia.nivelPrioridad()).isEqualTo("BAJA");
    }

    @Test
    void archivoTerritorialNoDisponibleNoSePresentaComoListaVaciaExitosa() throws Exception {
        Resource recursoInexistente = mock(Resource.class);
        when(recursoInexistente.getInputStream()).thenThrow(
            new java.io.IOException("archivo de prueba no disponible"));
        BarrioContextoService sinDatos = new BarrioContextoService(recursoInexistente);
        var modeloSinDatos = new ModeloPrioridadCoberturaService(sinDatos, rutaRepository);

        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of());
        var resultado = modeloSinDatos.analizarCobertura(false);

        assertThat(resultado.totalBarriosAnalizados()).isZero();
        assertThat(resultado.barriosPrioritarios()).isEmpty();
    }

    private static PrioridadCoberturaResponse.BarrioPrioridad barrio(
            List<PrioridadCoberturaResponse.BarrioPrioridad> barrios,
            String nombre) {
        return barrios.stream()
            .filter(item -> item.barrio().equalsIgnoreCase(nombre))
            .findFirst()
            .orElseThrow();
    }

    private static Ruta ruta(String nombreBarrio, Ruta.Estado estado) {
        Ruta ruta = new Ruta();
        ruta.setId(1L);
        ruta.setCodigo("RUT-1");
        ruta.setNombre("Ruta de prueba");
        ruta.setEstado(estado);
        ruta.setVerificada(true);
        ruta.setBarrios(List.of(nombreBarrio, "CENTRO"));
        ruta.setBarriosOrdenados(List.of(nombreBarrio, "CENTRO"));

        Parada parada = new Parada();
        parada.setId(1L);
        parada.setNombre("Parada de prueba");
        parada.setBarrio(nombreBarrio);
        parada.setEstado(Parada.Estado.ACTIVA);
        parada.setLatitud(null);
        parada.setLongitud(null);
        ruta.setParadas(new ArrayList<>(List.of(parada)));
        return ruta;
    }
}
