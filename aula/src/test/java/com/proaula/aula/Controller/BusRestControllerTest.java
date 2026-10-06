package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.dto.BusConsultaDto;

@ExtendWith(MockitoExtension.class)
class BusRestControllerTest {

    @Mock
    private BusService busService;

    @InjectMocks
    private BusRestController controller;

    @Test
    void getAllBusesWithRutaExposesOnlyUniqueRoutesToAnonymousUsers() throws Exception {
        Bus bus = crearBus();
        Bus secondBus = crearBus();
        secondBus.setId(4L);
        secondBus.setPlaca("XYZ789");
        when(busService.getAllBuses()).thenReturn(List.of(bus, secondBus));

        List<BusConsultaDto> result = controller.getAllBusesWithRuta(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRutaNombre()).isEqualTo("Centro");
        assertThat(result.get(0).getBarrios()).containsExactly("Centro");
        assertThat(result.get(0).getId()).isNull();
        assertThat(result.get(0).getPlaca()).isNull();
        assertThat(result.get(0).getModelo()).isNull();
        assertThat(result.get(0).getColor()).isNull();
        assertThat(result.get(0).getConductor()).isNull();
        assertThat(result.get(0).getHoraAproximada()).isNull();
        assertThat(result.get(0).getRutaVerificada()).isNull();
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json)
                .contains("rutaNombre", "barrios")
                .doesNotContain("placa", "modelo", "color", "conductor", "horaAproximada");
    }

    @Test
    void getAllBusesWithRutaIncludesFullBusAndRouteDetailsForAuthenticatedUsers() {
        Bus bus = crearBus();
        when(busService.getAllBuses()).thenReturn(List.of(bus));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "usuario", "credenciales", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        List<BusConsultaDto> result = controller.getAllBusesWithRuta(authentication);

        assertThat(result.get(0).getId()).isEqualTo(3L);
        assertThat(result.get(0).getPlaca()).isEqualTo("ABC123");
        assertThat(result.get(0).getModelo()).isEqualTo("Bus de prueba");
        assertThat(result.get(0).getConductor()).isEqualTo("Conductor de prueba");
        assertThat(result.get(0).getBarrios()).containsExactly("Centro");
        assertThat(result.get(0).getHoraAproximada()).isEqualTo("07:30");
        assertThat(result.get(0).getRutaVerificada()).isTrue();
    }

    private Bus crearBus() {
        Ruta ruta = new Ruta();
        ruta.setId(7L);
        ruta.setNombre("Centro");
        ruta.setBarrios(List.of("Centro"));
        ruta.setHoraAproximada(LocalTime.of(7, 30));
        ruta.setVerificada(true);

        Bus bus = new Bus();
        bus.setId(3L);
        bus.setPlaca("ABC123");
        bus.setModelo("Bus de prueba");
        bus.setConductor("Conductor de prueba");
        bus.setRuta(ruta);
        return bus;
    }
}
