package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.BusService;

@SpringBootTest
@AutoConfigureMockMvc
class ConsultasPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusService busService;

    @Test
    void publicPageRendersOnlyRoutesAndNeighborhoods() throws Exception {
        when(busService.getAllBuses()).thenReturn(List.of(bus()));

        String page = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/consultas"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page)
                .contains("Ruta Centro", "Centro", "inicio-de-sesion-mejorado",
                        "bus-directory-page--public", "content=\"#f5f9fd\"")
                .doesNotContain("bus-directory-page--authenticated",
                        "ABC123", "Conductora Confidencial", "07:30", "Verificada");
    }

    @Test
    void authenticatedPageShowsPrivateBusInformationWithoutLoginLink() throws Exception {
        when(busService.getAllBuses()).thenReturn(List.of(bus()));
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", "N/A", List.of(() -> "ROLE_USER")));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        String page = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/consultas")
                                .session(session))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page)
                .contains("bus-directory-page--authenticated", "content=\"#101715\"",
                        "ABC123", "Conductora Confidencial", "Iniciar sesión", "07:30", "Verificada")
                .doesNotContain("bus-directory-page--public")
                .contains("href=\"/inicio-de-sesion-mejorado\"");
    }

    @Test
    void travelPlannerIsAvailableOnPublicAndAuthenticatedMaps() throws Exception {
        String publicPage = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viajar_public"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(publicPage)
                .contains("id=\"btnPlanificar\"", "id=\"plannerStatus\"", "Mejor ruta para llegar",
                        "Se recomienda una sola ruta: primero se prioriza una ruta directa",
                        "/css/travel.css?v=map-polish-2",
                        "/css/map-ui.css?v=map-polish-2",
                        "/js/map-base.js?v=map-polish-2",
                        "/js/map-travel.js?v=map-polish-2");

        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", "N/A", List.of(() -> "ROLE_USER")));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        String authenticatedPage = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viajar")
                                .session(session))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(authenticatedPage)
                .contains("id=\"btnPlanificar\"", "id=\"plannerStatus\"", "Mejor ruta para llegar",
                        "Se recomienda una sola ruta: primero se prioriza una ruta directa",
                        "/css/travel.css?v=map-polish-2",
                        "/css/map-ui.css?v=map-polish-2",
                        "/js/map-base.js?v=map-polish-2",
                        "/js/map-travel.js?v=map-polish-2");
    }

    private Bus bus() {
        Ruta ruta = new Ruta();
        ruta.setId(21L);
        ruta.setNombre("Ruta Centro");
        ruta.setBarrios(List.of("Centro", "Getsemaní"));
        ruta.setHoraAproximada(LocalTime.of(7, 30));
        ruta.setVerificada(true);

        Bus bus = new Bus();
        bus.setId(44L);
        bus.setPlaca("ABC123");
        bus.setModelo("Bus Urbano Test");
        bus.setColor("Azul");
        bus.setConductor("Conductora Confidencial");
        bus.setRuta(ruta);
        return bus;
    }
}
