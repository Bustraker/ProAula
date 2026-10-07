package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BarrioService;
import com.proaula.aula.Service.RutaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RutaRestControllerTest {

    @Mock
    private RutaService rutaService;

    @Mock
    private BarrioService barrioService;

    @InjectMocks
    private RutaRestController rutaRestController;

    @Test
    void rutasConCoordenadasSoloExponeAlPublicoBusesSinDatosDelConductor() {
        Ruta ruta = rutaVerificadaConBus();
        when(rutaService.getRutasVerificadas()).thenReturn(List.of(ruta));
        when(barrioService.obtenerCoordenadas(anyList())).thenReturn(Map.of());

        Map<String, Object> datosRuta = rutaRestController.obtenerRutasConCoordenadas(null)
            .getBody().get(0);
        List<?> buses = (List<?>) datosRuta.get("buses");
        Map<?, ?> bus = (Map<?, ?>) buses.get(0);

        assertThat(datosRuta.get("barriosOrdenados")).isEqualTo(List.of("Centro", "Manga"));
        assertThat(datosRuta.get("ordenBarriosConfirmado")).isEqualTo(true);
        assertThat(buses).hasSize(1);
        assertThat(bus.get("placa")).isEqualTo("ABC123");
        assertThat(bus.containsKey("conductor")).isFalse();
    }

    @Test
    void rutasConCoordenadasIncluyeConductorSoloParaUsuariosAutenticados() {
        Ruta ruta = rutaVerificadaConBus();
        when(rutaService.getRutasVerificadas()).thenReturn(List.of(ruta));
        when(barrioService.obtenerCoordenadas(anyList())).thenReturn(Map.of());
        var autenticacion = new UsernamePasswordAuthenticationToken(
            "usuario", "credencial", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Map<String, Object> datosRuta = rutaRestController.obtenerRutasConCoordenadas(autenticacion)
            .getBody().get(0);
        List<?> buses = (List<?>) datosRuta.get("buses");
        Map<?, ?> bus = (Map<?, ?>) buses.get(0);

        assertThat(bus.get("conductor")).isEqualTo("Conductor privado");
    }

    private Ruta rutaVerificadaConBus() {
        Ruta ruta = new Ruta();
        ruta.setId(1L);
        ruta.setNombre("Centro - Manga");
        ruta.setBarrios(List.of("Centro", "Manga"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga"));
        ruta.setVerificada(true);
        Bus bus = new Bus();
        bus.setId(2L);
        bus.setPlaca("ABC123");
        bus.setModelo("Bus");
        bus.setConductor("Conductor privado");
        ruta.getBuses().add(bus);
        return ruta;
    }
}
