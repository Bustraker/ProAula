package com.proaula.aula.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class ReportesAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void indicatorsEndpointReturnsCurrentAggregatesWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/admin/reportes/indicadores"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.actividadMensual", hasSize(12)))
                .andExpect(jsonPath("$.flotaPorEstado").isMap())
                .andExpect(jsonPath("$.flotaPorOcupacion").isMap())
                .andExpect(jsonPath("$.rutasPorEstado").isMap())
                .andExpect(jsonPath("$.incidentesPorTipo").isArray())
                .andExpect(jsonPath("$.datosSinteticosConfigurados").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsPageIncludesAnalyticsDashboard() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chartActivity\"")))
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
