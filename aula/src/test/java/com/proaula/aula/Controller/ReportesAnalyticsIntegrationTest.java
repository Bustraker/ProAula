package com.proaula.aula.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.proaula.aula.LegalConsentTestSupport;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.LegalConsentService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class ReportesAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LegalConsentService legalConsentService;

    @BeforeEach
    void consentedTestUsersExist() {
        LegalConsentTestSupport.ensureConsentedUser(
                usuarioRepository, legalConsentService, "admin", "ROLE_ADMIN");
        LegalConsentTestSupport.ensureConsentedUser(
                usuarioRepository, legalConsentService, "user", "ROLE_USER");
    }

    @Test
    void indicatorsEndpointReturnsCurrentAggregatesWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/admin/reportes/indicadores")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .sessionAttr("rolUsuario", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.actividadMensual", hasSize(12)))
                .andExpect(jsonPath("$.flotaPorEstado").isMap())
                .andExpect(jsonPath("$.flotaPorOcupacion").isMap())
                .andExpect(jsonPath("$.rutasPorEstado").isMap())
                .andExpect(jsonPath("$.incidentesPorTipo").isArray())
                .andExpect(jsonPath("$.prioridadCoberturaBarrios").isMap())
                .andExpect(jsonPath("$.prioridadCoberturaBarrios.nombreModelo")
                        .value("Prioridad potencial de revisión de cobertura"))
                .andExpect(jsonPath("$.prioridadCoberturaBarrios.versionModelo").value("1.0"))
                .andExpect(jsonPath("$.prioridadCoberturaBarrios.barriosPrioritarios").isArray())
                .andExpect(jsonPath("$.datosSinteticosConfigurados").value(false));
    }

    @Test
    void reportsPageIncludesAnalyticsDashboard() throws Exception {
        mockMvc.perform(get("/reportes")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .sessionAttr("rolUsuario", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chartActivity\"")))
                .andExpect(content().string(containsString("id=\"coveragePriorityChart\"")))
                .andExpect(content().string(containsString("Barrios para revisar cobertura")))
                .andExpect(content().string(containsString("reports-dashboard.js")))
                .andExpect(content().string(containsString("Rutas con más viajes registrados")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void indicatorsEndpointRemainsRestrictedToAdministrators() throws Exception {
        mockMvc.perform(get("/api/admin/reportes/indicadores"))
                .andExpect(status().isForbidden());
    }
}
