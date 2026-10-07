package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.RutaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusControllerTest {

    @Mock
    private BusService busService;

    @Mock
    private RutaService rutaService;

    @InjectMocks
    private BusController busController;

    @Test
    void actualizarBusCambiaIdentificacionYConservaDatosOperativosExistentes() {
        Ruta ruta = new Ruta();
        ruta.setId(7L);
        ruta.setNombre("Centro - Manga");

        Bus existente = new Bus();
        existente.setId(12L);
        existente.setPlaca("ABC-123");
        existente.setModelo("Modelo anterior");
        existente.setColor("Azul");
        existente.setConductor("Ana Pérez");
        existente.setRuta(ruta);

        Bus formulario = new Bus();
        formulario.setPlaca("XYZ-987");
        formulario.setModelo("Modelo actualizado");

        when(busService.getBusById(12L)).thenReturn(existente);

        String resultado = busController.actualizarBus(12L, formulario);

        assertThat(resultado).isEqualTo("redirect:/index_2");
        assertThat(existente.getPlaca()).isEqualTo("XYZ-987");
        assertThat(existente.getModelo()).isEqualTo("Modelo actualizado");
        assertThat(existente.getColor()).isEqualTo("Azul");
        assertThat(existente.getConductor()).isEqualTo("Ana Pérez");
        assertThat(existente.getRuta()).isSameAs(ruta);
        verify(busService).saveBus(existente);
    }
}
