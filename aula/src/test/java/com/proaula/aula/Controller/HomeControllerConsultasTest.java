package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.ContactoMensajeService;
import com.proaula.aula.Service.RutaService;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.Service.ViajeService;
import com.proaula.aula.dto.BusConsultaDto;

@ExtendWith(MockitoExtension.class)
class HomeControllerConsultasTest {

    @Mock
    private BusService busService;
    @Mock
    private RutaService rutaService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private ViajeService viajeService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ContactoMensajeService contactoMensajeService;

    @InjectMocks
    private HomeController controller;

    @Test
    void consultasProvidesOnlyRouteAndNeighborhoodsToPublicVisitors() {
        when(busService.getBusConsultations(false)).thenReturn(List.of(
                new BusConsultaDto(null, null, null, null, null,
                        "Centro", List.of("Centro"), null, null)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.consultas(model, null);

        Object busAttribute = model.getAttribute("buses");
        assertThat(busAttribute).isInstanceOf(List.class);
        Object firstBus = ((List<?>) busAttribute).get(0);
        assertThat(firstBus).isInstanceOf(BusConsultaDto.class);
        BusConsultaDto route = (BusConsultaDto) firstBus;
        assertThat(view).isEqualTo("Usuario/consultas");
        assertThat(model.getAttribute("autenticado")).isEqualTo(false);
        assertThat(route.getRutaNombre()).isEqualTo("Centro");
        assertThat(route.getBarrios()).containsExactly("Centro");
        assertThat(route.getId()).isNull();
        assertThat(route.getPlaca()).isNull();
        assertThat(route.getModelo()).isNull();
        assertThat(route.getColor()).isNull();
        assertThat(route.getConductor()).isNull();
        assertThat(route.getHoraAproximada()).isNull();
        assertThat(route.getRutaVerificada()).isNull();
        verify(busService).getBusConsultations(false);
    }

    @Test
    void consultasProvidesBusAndRouteDetailsToAuthenticatedUsers() {
        when(busService.getBusConsultations(true)).thenReturn(List.of(
                new BusConsultaDto(12L, "ABC123", "Bus urbano", "Azul",
                        "Conductor de prueba", "Centro", List.of("Centro"),
                        "07:30", true)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "usuario", "credenciales", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.consultas(model, authentication);

        Object busAttribute = model.getAttribute("buses");
        assertThat(busAttribute).isInstanceOf(List.class);
        Object firstBus = ((List<?>) busAttribute).get(0);
        assertThat(firstBus).isInstanceOf(BusConsultaDto.class);
        BusConsultaDto bus = (BusConsultaDto) firstBus;
        assertThat(model.getAttribute("autenticado")).isEqualTo(true);
        assertThat(bus.getId()).isEqualTo(12L);
        assertThat(bus.getPlaca()).isEqualTo("ABC123");
        assertThat(bus.getModelo()).isEqualTo("Bus urbano");
        assertThat(bus.getColor()).isEqualTo("Azul");
        assertThat(bus.getConductor()).isEqualTo("Conductor de prueba");
        assertThat(bus.getHoraAproximada()).isEqualTo("07:30");
        assertThat(bus.getRutaVerificada()).isTrue();
        verify(busService).getBusConsultations(true);
    }
}
