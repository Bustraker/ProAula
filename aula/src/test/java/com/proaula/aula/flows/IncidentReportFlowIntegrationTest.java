package com.proaula.aula.flows;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.IncidenteRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.RutaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class IncidentReportFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private IncidenteRepository incidenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RutaService rutaService;

    @Test
    void authenticatedUserCanOpenIncidentReportForm() throws Exception {
        Usuario usuario = createUser();
        createRoute();

        mockMvc.perform(get("/incidentes/nuevo")
                        .with(user(usuario.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Reportar un incidente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Ruta afectada")));
    }

    @Test
    void authenticatedUserCanSubmitIncidentAndItIsStoredWithReporterAndTimestamp() throws Exception {
        Usuario usuario = createUser();
        Ruta ruta = createRoute();

        mockMvc.perform(post("/incidentes")
                        .with(user(usuario.getUsername()).roles("USER"))
                        .with(csrf())
                        .param("tipo", "RETRASO")
                        .param("rutaId", ruta.getId().toString())
                        .param("descripcion", "El bus presenta un retraso en el recorrido."))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/incidentes/nuevo"));

        Incidente incidente = incidenteRepository.findAll().stream()
                .filter(item -> item.getUsuario().getId().equals(usuario.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(incidente.getTipo()).isEqualTo("RETRASO");
        assertThat(incidente.getRuta().getId()).isEqualTo(ruta.getId());
        assertThat(incidente.getDescripcion()).isEqualTo("El bus presenta un retraso en el recorrido.");
        assertThat(incidente.getEstado()).isEqualTo("PENDIENTE");
        assertThat(incidente.getFechaReporte()).isNotNull();
        assertThat(rutaService.hasRelatedRecords(ruta.getId())).isTrue();
    }

    @Test
    void missingDescriptionShowsValidationMessageWithoutSavingIncident() throws Exception {
        Usuario usuario = createUser();
        Ruta ruta = createRoute();

        mockMvc.perform(post("/incidentes")
                        .with(user(usuario.getUsername()).roles("USER"))
                        .with(csrf())
                        .param("tipo", "ACCIDENTE")
                        .param("rutaId", ruta.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Escribe una descripción del incidente.")));

        assertThat(incidenteRepository.findAll())
                .noneMatch(item -> item.getUsuario().getId().equals(usuario.getId()));
    }

    @Test
    void apiRequiresAuthenticationAndValidatesIncidentRequest() throws Exception {
        mockMvc.perform(get("/incidentes/nuevo"))
                .andExpect(status().is3xxRedirection());

        Usuario usuario = createUser();
        mockMvc.perform(post("/api/incidentes")
                        .with(user(usuario.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VALIDATION_ERROR")));
    }

    @Test
    void apiCreatesIncidentAndReturnsCreatedRepresentation() throws Exception {
        Usuario usuario = createUser();
        Ruta ruta = createRoute();

        mockMvc.perform(post("/api/incidentes")
                        .with(user(usuario.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "tipo": "FALLA_SERVICIO",
                                  "rutaId": %d,
                                  "descripcion": "El bus presenta una falla mecánica."
                                }
                                """.formatted(ruta.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"estado\":\"PENDIENTE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rutaId\":" + ruta.getId())));

        assertThat(incidenteRepository.findAll())
                .anySatisfy(item -> {
                    assertThat(item.getUsuario().getId()).isEqualTo(usuario.getId());
                    assertThat(item.getTipo()).isEqualTo("FALLA_SERVICIO");
                    assertThat(item.getFechaReporte()).isNotNull();
                });
    }

    private Usuario createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario usuario = new Usuario();
        usuario.setUsername("ir" + suffix);
        usuario.setPassword(passwordEncoder.encode("test-password"));
        usuario.setNombres("Usuario");
        usuario.setApellidos("Incidente");
        usuario.setEmail("incident-" + suffix + "@example.test");
        usuario.setRole("ROLE_USER");
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Ruta createRoute() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta para incidente " + suffix);
        ruta.setOrigen("Centro");
        ruta.setDestino("Manga");
        ruta.setVerificada(true);
        return rutaRepository.saveAndFlush(ruta);
    }
}
