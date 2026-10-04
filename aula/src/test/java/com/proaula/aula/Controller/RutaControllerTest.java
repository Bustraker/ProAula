package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.RutaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RutaControllerTest {

    @Mock
    private RutaService rutaService;

    @InjectMocks
    private RutaController rutaController;

    @Test
    void agregarRutaPersisteElOrdenConfirmadoEnAmbasColecciones() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Centro - Manga");

        rutaController.agregarRuta(ruta, " Centro, Manga, Crespo ");

        ArgumentCaptor<Ruta> rutaGuardada = ArgumentCaptor.forClass(Ruta.class);
        verify(rutaService).saveRuta(rutaGuardada.capture());
        assertThat(rutaGuardada.getValue().getBarrios()).containsExactly("Centro", "Manga", "Crespo");
        assertThat(rutaGuardada.getValue().getBarriosOrdenados()).containsExactly("Centro", "Manga", "Crespo");
        assertThat(rutaGuardada.getValue().isOrdenBarriosConfirmado()).isTrue();
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

        rutaController.guardarEdicionRuta(5L, formulario, "Centro, Manga, Crespo");

        verify(rutaService).saveRuta(existente);
        assertThat(existente.getBarriosOrdenados()).containsExactly("Centro", "Manga", "Crespo");
        assertThat(existente.isOrdenBarriosConfirmado()).isTrue();
        assertThat(existente.getVerificada()).isTrue();
    }
}
