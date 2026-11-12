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
    void consultasProvidesBusAndRouteDetails() {
        when(busService.getBusConsultations()).thenReturn(List.of(
                new BusConsultaDto(12L, "ABC123", "Bus urbano", "Azul",
                        "Conductor de prueba", "Centro", List.of("Centro"),
                        "07:30", true)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.consultas(model);

        Object busAttribute = model.getAttribute("buses");
        assertThat(busAttribute).isInstanceOf(List.class);
        Object firstBus = ((List<?>) busAttribute).get(0);
        assertThat(firstBus).isInstanceOf(BusConsultaDto.class);
        BusConsultaDto bus = (BusConsultaDto) firstBus;
        assertThat(view).isEqualTo("Usuario/consultas");
        assertThat(model.getAttribute("autenticado")).isEqualTo(true);
        assertThat(bus.getId()).isEqualTo(12L);
        assertThat(bus.getPlaca()).isEqualTo("ABC123");
        assertThat(bus.getModelo()).isEqualTo("Bus urbano");
        assertThat(bus.getColor()).isEqualTo("Azul");
        assertThat(bus.getConductor()).isEqualTo("Anónimo");
        assertThat(bus.getHoraAproximada()).isEqualTo("07:30");
        assertThat(bus.getRutaVerificada()).isTrue();
        verify(busService).getBusConsultations();
    }
}
