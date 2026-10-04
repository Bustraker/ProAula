package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BusService;

@ExtendWith(MockitoExtension.class)
class BusRestControllerTest {

    @Mock
    private BusService busService;

    @InjectMocks
    private BusRestController controller;

    @Test
    void getAllBusesWithRutaDoesNotExposeDriverToAnonymousUsers() {
        Bus bus = crearBus();
        when(busService.getAllBuses()).thenReturn(List.of(bus));

        List<Map<String, Object>> result = controller.getAllBusesWithRuta(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("placa", "ABC123")
                .containsEntry("rutaNombre", "Centro")
                .doesNotContainKey("conductor");
    }

    @Test
    void getAllBusesWithRutaIncludesDriverForAuthenticatedUsers() {
        Bus bus = crearBus();
        when(busService.getAllBuses()).thenReturn(List.of(bus));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "usuario", "credenciales", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        List<Map<String, Object>> result = controller.getAllBusesWithRuta(authentication);

        assertThat(result.get(0)).containsEntry("conductor", "Conductor de prueba");
    }

    private Bus crearBus() {
        Ruta ruta = new Ruta();
        ruta.setId(7L);
        ruta.setNombre("Centro");

        Bus bus = new Bus();
        bus.setId(3L);
        bus.setPlaca("ABC123");
        bus.setModelo("Bus de prueba");
        bus.setConductor("Conductor de prueba");
        bus.setRuta(ruta);
        return bus;
    }
}
