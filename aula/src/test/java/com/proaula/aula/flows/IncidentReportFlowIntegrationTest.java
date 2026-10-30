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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        mockMvc.perform(get("/api/incidentes"))
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

    @Test
    void authenticatedUserCanReportButCannotViewIncidentPanel() throws Exception {
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
                                  "descripcion": "El bus presenta una falla."
                                }
                                """.formatted(ruta.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/incidentes/panel")
                        .with(user(usuario.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/incidentes")
                        .with(user(usuario.getUsername()).roles("USER"))
                        .param("tipo", "FALLA_SERVICIO")
                        .param("ruta", ruta.getNombre()))
                .andExpect(status().isForbidden());
    }

    @Test
    void incidentPagesAndApiRejectAnonymousAndUnauthorizedUsers() throws Exception {
        for (String path : new String[]{"/incidentes/nuevo", "/incidentes/panel", "/api/incidentes"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location",
                            "http://localhost/inicio-de-sesion-mejorado"));
            mockMvc.perform(get(path).with(user("guest").roles("GUEST")))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/incidentes").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/incidentes").with(user("guest").roles("GUEST")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/incidentes")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/incidentes")
                        .with(user("guest").roles("GUEST"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReportAndViewIncidentsThroughPageAndApi() throws Exception {
        Usuario administrador = createUser();
        administrador.setRole("ROLE_ADMIN");
        usuarioRepository.saveAndFlush(administrador);
        Ruta ruta = createRoute();

        mockMvc.perform(get("/incidentes/nuevo")
                        .with(user(administrador.getUsername()).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/incidentes")
                        .with(user(administrador.getUsername()).roles("ADMIN"))
                        .with(csrf())
                        .param("tipo", "ACCIDENTE")
                        .param("rutaId", ruta.getId().toString())
                        .param("descripcion", "Accidente reportado por administracion."))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/incidentes/panel")
                        .with(user(administrador.getUsername()).roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/incidentes")
                        .with(user(administrador.getUsername()).roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "tipo": "RETRASO",
                                  "rutaId": %d,
                                  "descripcion": "Retraso reportado por administracion."
                                }
                                """.formatted(ruta.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/incidentes")
                        .with(user(administrador.getUsername()).roles("ADMIN"))
                        .param("ruta", ruta.getNombre()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/incidentes")
                        .with(user(administrador.getUsername()).roles("ADMIN"))
                        .param("ruta", ruta.getNombre())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true));

        mockMvc.perform(get("/api/incidentes")
                        .with(user(administrador.getUsername()).roles("ADMIN"))
                        .param("ruta", ruta.getNombre())
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.last").value(true));

        assertThat(incidenteRepository.findAll())
                .anySatisfy(item -> assertThat(item.getUsuario().getId()).isEqualTo(administrador.getId()));
    }

    @Test
    void administratorCanChangeIncidentStateAndUsersCannot() throws Exception {
        Usuario reportero = createUser();
        Ruta ruta = createRoute();
        Incidente incidente = new Incidente();
        incidente.setUsuario(reportero);
        incidente.setRuta(ruta);
        incidente.setTipo("ACCIDENTE");
        incidente.setTitulo("Accidente reportado");
        incidente.setDescripcion("Incidente para prueba de cambio de estado.");
        incidente = incidenteRepository.saveAndFlush(incidente);

        mockMvc.perform(patch("/api/incidentes/{id}/estado", incidente.getId())
                        .with(user(reportero.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"estado":"EN_ATENCION"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/incidentes/{id}/estado", incidente.getId())
                        .with(user("incident-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"estado":" en_atencion "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidente.getId()))
                .andExpect(jsonPath("$.estado").value("EN_ATENCION"));

        assertThat(incidenteRepository.findById(incidente.getId()).orElseThrow().getEstado())
                .isEqualTo("EN_ATENCION");
    }

    @Test
    void changingIncidentStateValidatesStatusAndIncidentExistence() throws Exception {
        mockMvc.perform(patch("/api/incidentes/{id}/estado", 404L)
                        .with(user("incident-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"estado":"RESUELTO"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INCIDENT_NOT_FOUND"));

        mockMvc.perform(patch("/api/incidentes/{id}/estado", 1L)
                        .with(user("incident-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"estado":"CANCELADO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void incidentApiRejectsMalformedIdsAndInvalidPagination() throws Exception {
        mockMvc.perform(patch("/api/incidentes/no-es-numero/estado")
                        .with(user("incident-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"estado":"RESUELTO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/incidentes")
                        .with(user("incident-admin").roles("ADMIN"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/incidentes")
                        .with(user("incident-admin").roles("ADMIN"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
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
