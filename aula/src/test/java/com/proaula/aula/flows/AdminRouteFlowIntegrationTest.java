package com.proaula.aula.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Repository.RutaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
@Transactional
class AdminRouteFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RutaRepository rutaRepository;

    @Test
    void routeAdministrationRequiresAdminAndCreatesAConfirmedPublicRoute() throws Exception {
        mockMvc.perform(get("/rutas/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost/inicio-de-sesion-mejorado"));

        mockMvc.perform(get("/rutas/admin").with(user("ordinary-user").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/rutas/admin").with(user("route-admin").roles("ADMIN")))
                .andExpect(status().isOk());

        long rutasAntes = rutaRepository.count();
        mockMvc.perform(post("/rutas")
                        .with(user("route-admin").roles("ADMIN"))
                        .with(csrf())
                        .param("nombre", "Ruta Creada por Admin")
                        .param("origen", "Centro")
                        .param("destino", "Manga")
                        .param("verificada", "true")
                        .param("barriosOrdenadosTexto", "Centro, Manga"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/rutas"));

        assertThat(rutaRepository.count()).isEqualTo(rutasAntes + 1);
        assertThat(rutaRepository.findAllByVerificadaTrue())
                .anySatisfy(ruta -> {
                    assertThat(ruta.getNombre()).isEqualTo("Ruta Creada por Admin");
                    assertThat(ruta.getBarriosOrdenados()).containsExactly("Centro", "Manga");
                    assertThat(ruta.isOrdenBarriosConfirmado()).isTrue();
                });
    }
}
