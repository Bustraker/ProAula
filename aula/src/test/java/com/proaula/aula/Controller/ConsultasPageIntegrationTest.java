package com.proaula.aula.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proaula.aula.dto.BusConsultaDto;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.LegalConsentService;
import com.proaula.aula.LegalConsentTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class ConsultasPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusService busService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LegalConsentService legalConsentService;

    @BeforeEach
    void consentedSessionUserExists() {
        LegalConsentTestSupport.ensureConsentedUser(
                usuarioRepository, legalConsentService, "usuario", "ROLE_USER");
    }

    @Test
    void busConsultationPageRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/consultas"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/buses/todos"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void publicLandingDoesNotLinkToBusConsultation() throws Exception {
        String page = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/index_3_public"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page)
                .contains("Planifica tu recorrido sin iniciar sesión.",
                        "Cartagena,",
                        "MOVILIDAD LOCAL",
                        "scene-map",
                        "scene-bus",
                        "map-route",
                        "Moverse por Cartagena debería sentirse más claro.",
                        "Menos búsquedas. Más claridad para moverte.",
                        "Busca por barrios",
                        "id=\"como-funciona\"",
                        "Tu camino, en tres momentos.",
                        "La información de tu viaje no debería estar dispersa.",
                        "Lo que imaginamos para el siguiente tramo.",
                        "Idea por evaluar",
                        "/css/public-home.css?v=cartagena-contacto-15",
                        "/js/index_3_public.js?v=route-map-3",
                        "name=\"_csrf\"",
                        "name=\"_csrf_header\"")
                .doesNotContain("/consultas", "Consulta de buses", "Consultar buses",
                        "puzzle-piece--origin", "puzzle-shuttle");
    }

    @Test
    void authenticatedPageShowsPrivateBusInformationWithoutLoginLink() throws Exception {
        when(busService.getBusConsultations()).thenReturn(List.of(
                new BusConsultaDto(44L, "ABC123", "Bus Urbano Test", "Azul",
                        "Conductora Confidencial", "Ruta Centro",
                        List.of("Centro", "Getsemaní"), "07:30", true)));
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", "N/A", List.of(() -> "ROLE_USER")));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        String page = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/consultas")
                                .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page)
                .contains("bus-directory-page--authenticated", "content=\"#101715\"",
                        "ABC123", "Anónimo", "Mi cuenta", "07:30", "Verificada")
                .doesNotContain("bus-directory-page--public")
                .contains("href=\"/dashboard\"")
                .doesNotContain("href=\"/inicio-de-sesion-mejorado\"");

        String busApi = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/buses/todos")
                                .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(busApi).contains("ABC123", "Anónimo").doesNotContain("Conductora Confidencial");
    }

    @Test
    void travelPlannerIsAvailableOnPublicAndAuthenticatedMaps() throws Exception {
        String publicPage = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viajar_public"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(publicPage)
                .contains("id=\"btnPlanificar\"", "id=\"plannerStatus\"", "Rutas disponibles",
                        "/css/travel.css?v=route-polish-12",
                        "/css/map-ui.css?v=trips-4",
                        "/js/map-base.js?v=map-polish-8",
                        "/js/map-travel.js?v=trips-9");

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
                .contains("id=\"btnPlanificar\"", "id=\"plannerStatus\"", "Rutas disponibles",
                        "/css/travel.css?v=route-polish-12",
                        "/css/map-ui.css?v=trips-5",
                        "/js/map-base.js?v=map-polish-8",
                        "/js/map-travel.js?v=trips-9");
    }
}
