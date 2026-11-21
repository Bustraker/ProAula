package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BarrioService;
import com.proaula.aula.Service.RutaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RutaControllerTest {

    @Mock
    private RutaService rutaService;

    private RutaController rutaController;

    @BeforeEach
    void setUp() {
        rutaController = new RutaController(rutaService, new BarrioService());
    }

    @Test
    void agregarRutaPersisteElOrdenConfirmadoEnAmbasColecciones() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Centro - Manga");

        rutaController.agregarRuta(ruta, " Centro, Manga, Crespo ");

        ArgumentCaptor<Ruta> rutaGuardada = ArgumentCaptor.forClass(Ruta.class);
        verify(rutaService).saveRuta(rutaGuardada.capture());
        assertThat(rutaGuardada.getValue().getBarrios()).containsExactly("CENTRO", "MANGA", "CRESPO");
        assertThat(rutaGuardada.getValue().getBarriosOrdenados()).containsExactly("CENTRO", "MANGA", "CRESPO");
        assertThat(rutaGuardada.getValue().getOrigen()).isEqualTo("CENTRO");
        assertThat(rutaGuardada.getValue().getDestino()).isEqualTo("CRESPO");
        assertThat(rutaGuardada.getValue().isOrdenBarriosConfirmado()).isTrue();
    }

    @Test
    void agregarRutaRechazaNombresFueraDelCatalogoCatastral() {
        String resultado = rutaController.agregarRuta(new Ruta(), "Centro, Barrio inventado, Crespo");

        assertThat(resultado).isEqualTo("redirect:/rutas/admin?error=barrios");
        verifyNoInteractions(rutaService);
    }

    @Test
    void agregarRutaRechazaSecuenciasConMenosDeDosBarriosDistintos() {
        String resultado = rutaController.agregarRuta(new Ruta(), "Centro, Centro");

        assertThat(resultado).isEqualTo("redirect:/rutas/admin?error=barrios");
        verifyNoInteractions(rutaService);
    }

    @Test
    void guardarEdicionRutaGuardaOrdenEscritoManual() {
        Ruta existente = new Ruta();
        existente.setId(5L);
        existente.setBarrios(List.of("Crespo", "Centro", "Manga"));
        Ruta formulario = new Ruta();
        formulario.setVerificada(true);
        when(rutaService.getRutaById(5L)).thenReturn(existente);

        rutaController.guardarEdicionRuta(5L, formulario, "Centro, Manga, Crespo", null);

        verify(rutaService).saveRuta(existente, true);
        assertThat(existente.getBarriosOrdenados()).containsExactly("CENTRO", "MANGA", "CRESPO");
        assertThat(existente.getOrigen()).isEqualTo("CENTRO");
        assertThat(existente.getDestino()).isEqualTo("CRESPO");
        assertThat(existente.isOrdenBarriosConfirmado()).isTrue();
        assertThat(existente.getVerificada()).isTrue();
    }

    @Test
    void guardarEdicionRutaActualizaAccesibilidadYMarcaLaRutaComoModificada() {
        Ruta existente = new Ruta();
        existente.setId(8L);
        existente.setBarrios(List.of("CENTRO", "MANGA"));
        existente.setBarriosOrdenados(List.of("CENTRO", "MANGA"));
        existente.setVerificada(true);
        Ruta formulario = new Ruta();
        formulario.setVerificada(true);
        when(rutaService.getRutaById(8L)).thenReturn(existente);

        rutaController.guardarEdicionRuta(8L, formulario, "Centro, Manga", true);

        assertThat(existente.getAccesibleMovilidadReducida()).isTrue();
        verify(rutaService).saveRuta(existente, true);
    }

    @Test
    void listarRutasNormalizaElFiltroAlNombreCatastral() {
        Ruta ruta = rutaActiva("Ruta Centro", true);
        ruta.setBarrios(List.of("CENTRO"));
        when(rutaService.getAllRutas()).thenReturn(List.of(ruta));

        ExtendedModelMap model = new ExtendedModelMap();
        rutaController.listarRutas(model, null, "Centro", null);

        assertThat(model.getAttribute("rutas")).isEqualTo(List.of(ruta));
        assertThat(model.getAttribute("barriosDisponibles")).isEqualTo(List.of("CENTRO"));
        assertThat(model.getAttribute("barrioSeleccionado")).isEqualTo("CENTRO");
    }

    @Test
    void listarRutasPermiteFiltrarAccesiblesNoAccesiblesYSinDatos() {
        Ruta accesible = rutaActiva("Accesible", true);
        Ruta noAccesible = rutaActiva("No accesible", false);
        Ruta sinDatos = rutaActiva("Sin datos", null);
        when(rutaService.getAllRutas()).thenReturn(List.of(accesible, noAccesible, sinDatos));

        ExtendedModelMap model = new ExtendedModelMap();
        rutaController.listarRutas(model, null, null, "accesible");
        assertThat(model.getAttribute("rutas")).isEqualTo(List.of(accesible));

        rutaController.listarRutas(model, null, null, "no-accesible");
        assertThat(model.getAttribute("rutas")).isEqualTo(List.of(noAccesible));

        rutaController.listarRutas(model, null, null, "sin-datos");
        assertThat(model.getAttribute("rutas")).isEqualTo(List.of(sinDatos));
    }

    private Ruta rutaActiva(String nombre, Boolean accesible) {
        Ruta ruta = new Ruta();
        ruta.setNombre(nombre);
        ruta.setAccesibleMovilidadReducida(accesible);
        return ruta;
    }
}
