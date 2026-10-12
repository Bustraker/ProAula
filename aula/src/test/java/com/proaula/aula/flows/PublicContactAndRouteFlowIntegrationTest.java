package com.proaula.aula.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Agencia;
import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Repository.AgenciaRepository;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ContactoMensajeRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class PublicContactAndRouteFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactoMensajeRepository contactoMensajeRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private ParadaRepository paradaRepository;

    @Autowired
    private RutaParadaRepository rutaParadaRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private AgenciaRepository agenciaRepository;

    @Test
    void publicContactSubmissionPersistsAndShowsAcknowledgement() throws Exception {
        long mensajesAntes = contactoMensajeRepository.count();

        mockMvc.perform(post("/contacto_public")
                        .with(csrf())
                        .param("nombre", "Ana")
                        .param("apellido", "Prueba")
                        .param("email", "ana@example.test")
                        .param("telefono", "3005550101")
                        .param("mensaje", "Consulta sobre una ruta"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Gracias por tu consulta. Nos pondremos en contacto pronto.")));

        assertThat(contactoMensajeRepository.count()).isEqualTo(mensajesAntes + 1);
        assertThat(contactoMensajeRepository.findAll())
                .anySatisfy(mensaje -> assertThat(mensaje.getEmail()).isEqualTo("ana@example.test"));
    }

    @Test
    void verifiedRoutesAreAvailableInPublicHtmlAndJsonMapEndpoints() throws Exception {
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta Integracion Publica");
        ruta.setOrigen("Centro");
        ruta.setDestino("Manga");
        ruta.setVerificada(true);
        ruta.setBarrios(List.of("Centro", "Manga"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga"));
        Ruta guardada = rutaRepository.saveAndFlush(ruta);

        mockMvc.perform(get("/rutas"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ruta Integracion Publica")));

        mockMvc.perform(get("/ruta/{id}", guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ruta Integracion Publica")));

        mockMvc.perform(get("/api/rutas-con-coordenadas"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ruta Integracion Publica")));

        mockMvc.perform(get("/api/rutas"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ruta Integracion Publica")));

        mockMvc.perform(get("/viajar_public"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btnPlanificar")));
    }

    @Test
    void provisionalRoutesAreVisibleWithWarningsAndOrderedDirectionsButExcludedFromTripApi() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta provisional " + suffix);
        ruta.setVerificada(false);
        ruta.setBarrios(List.of("Zona provisional A " + suffix, "Zona provisional B " + suffix));
        ruta.setBarriosOrdenados(ruta.getBarrios());
        Ruta rutaGuardada = rutaRepository.saveAndFlush(ruta);

        addRouteStop(rutaGuardada, "Parada ida inicial " + suffix, 1, 1, RutaParada.Sentido.IDA);
        addRouteStop(rutaGuardada, "Parada ida final " + suffix, 2, 2, RutaParada.Sentido.IDA);
        addRouteStop(rutaGuardada, "Parada vuelta inicial " + suffix, 3, 1, RutaParada.Sentido.VUELTA);
        addRouteStop(rutaGuardada, "Parada vuelta final " + suffix, 4, 2, RutaParada.Sentido.VUELTA);

        String rutasHtml = mockMvc.perform(get("/rutas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rutasHtml).contains("Ruta provisional " + suffix, "Provisional",
                "no están habilitados para planear viajes");

        String detalleHtml = mockMvc.perform(get("/ruta/{id}", rutaGuardada.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detalleHtml).contains("Información provisional, pendiente de verificación",
                "Paradas por sentido", "Ida", "Vuelta",
                "Parada ida inicial " + suffix, "Parada vuelta inicial " + suffix);
        assertThat(detalleHtml.indexOf("Parada ida inicial " + suffix))
                .isLessThan(detalleHtml.indexOf("Parada ida final " + suffix));
        assertThat(detalleHtml.indexOf("Parada vuelta inicial " + suffix))
                .isLessThan(detalleHtml.indexOf("Parada vuelta final " + suffix));

        String mapJson = mockMvc.perform(get("/api/rutas-con-coordenadas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String routeJson = mockMvc.perform(get("/api/rutas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapJson).doesNotContain("Ruta provisional " + suffix);
        assertThat(routeJson).doesNotContain("Ruta provisional " + suffix);

        String editorHtml = mockMvc.perform(get("/editar-ruta/{id}", rutaGuardada.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(editorHtml).contains("Orden pendiente de confirmar", "Zona provisional A " + suffix);
        assertThat(editorHtml).doesNotContain("Zona provisional A " + suffix + ", Zona provisional B " + suffix);
    }

    private void addRouteStop(Ruta ruta, String name, int stopOrder, int routeOrder,
                              RutaParada.Sentido sentido) {
        Parada parada = new Parada();
        parada.setNombre(name);
        parada.setOrden(stopOrder);
        parada.setBarrio("Barrio de prueba");
        parada.setRuta(ruta);
        Parada paradaGuardada = paradaRepository.saveAndFlush(parada);

        RutaParada relacion = new RutaParada();
        relacion.setRuta(ruta);
        relacion.setParada(paradaGuardada);
        relacion.setOrden(routeOrder);
        relacion.setSentido(sentido);
        rutaParadaRepository.saveAndFlush(relacion);
    }

    @Test
    void busApiSerializesStableResponseDtoWithRelatedNames() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta bus DTO " + suffix);
        Ruta rutaGuardada = rutaRepository.saveAndFlush(ruta);

        Agencia agencia = new Agencia();
        agencia.setNombre("Agencia DTO " + suffix);
        Agencia agenciaGuardada = agenciaRepository.saveAndFlush(agencia);

        Bus bus = new Bus();
        bus.setPlaca("B" + suffix);
        bus.setModelo("Modelo DTO");
        bus.setRuta(rutaGuardada);
        bus.setAgencia(agenciaGuardada);
        Bus busGuardado = busRepository.saveAndFlush(bus);

        mockMvc.perform(get("/api/buses/{id}", busGuardado.getId())
                        .with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.rutaNombre").value("Ruta bus DTO " + suffix))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.agenciaNombre").value("Agencia DTO " + suffix))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("hibernateLazyInitializer"))));
    }
}
