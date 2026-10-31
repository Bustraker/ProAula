package com.proaula.aula.flows;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Service.LlegadaBusService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
@Transactional
class LlegadaBusApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RutaRepository rutaRepository;
    @Autowired
    private ParadaRepository paradaRepository;
    @Autowired
    private RutaParadaRepository rutaParadaRepository;
    @Autowired
    private LlegadaBusService llegadaBusService;

    private Ruta ruta;
    private String usuario;
    private Parada paradaCentro;

    @BeforeEach
    void crearRutaDePrueba() {
        usuario = "llegadas-" + UUID.randomUUID();
        ruta = new Ruta();
        ruta.setNombre("Ruta temporal de pruebas");
        ruta.setBarrios(List.of("Centro", "Manga", "Crespo"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga", "Crespo"));
        ruta.setVerificada(true);
        ruta = rutaRepository.saveAndFlush(ruta);

        paradaCentro = guardarParada("Centro", 10.400, -75.500, 1);
        guardarParada("Manga", 10.410, -75.500, 2);
        guardarParada("Crespo", 10.420, -75.500, 3);
    }

    @Test
    void consultaPublicaNoExponeLaIdentidadNiLaUbicacionDelReportante() throws Exception {
        mockMvc.perform(get("/api/llegadas/{rutaId}", ruta.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("SIN_REPORTES"))
            .andExpect(jsonPath("$.llegadas").isEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(usuario))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("latitud"))));
    }

    @Test
    void publicarUbicacionRequiereSesionYProteccionCsrf() throws Exception {
        String ubicacion = "{\"latitud\":10.401,\"longitud\":-75.500,\"precisionMetros\":10}";
        String endpoint = "/api/llegadas/" + ruta.getId() + "/reportes?sentido=IDA";

        mockMvc.perform(post(endpoint)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ubicacion))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post(endpoint)
                .with(user(usuario).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ubicacion))
            .andExpect(status().isForbidden());

        mockMvc.perform(post(endpoint)
                .with(user(usuario).roles("USER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ubicacion))
            .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/llegadas/{rutaId}", ruta.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("SIN_VELOCIDAD"))
            .andExpect(jsonPath("$.reportantesActivos").value(1))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(usuario))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("10.401"))));
    }

    @Test
    void unUsuarioPuedeCesarSusReportesGps() throws Exception {
        llegadaBusService.reportarUbicacion(
            ruta.getId(), RutaParada.Sentido.IDA, usuario,
            new com.proaula.aula.dto.UbicacionBusReporteRequest(10.401, -75.500, 10.0));

        mockMvc.perform(delete("/api/llegadas/reportes/actual")
                .with(user(usuario).roles("USER"))
                .with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/llegadas/{rutaId}", ruta.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("SIN_REPORTES"));
    }

    @Test
    void administradorPuedeRegistrarCoordenadasDeUnaParadaSinModificarOtrosDatos() throws Exception {
        mockMvc.perform(get("/admin/coordenadas-paradas")
                .with(user("admin-prueba").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Coordenadas registradas")))
            .andExpect(content().string(containsString("Capturar GPS aquí")));

        mockMvc.perform(post("/admin/paradas/{id}/coordenadas", paradaCentro.getId())
                .with(user("admin-prueba").roles("ADMIN"))
                .with(csrf())
                .param("latitud", "10.401234")
                .param("longitud", "-75.501234"))
            .andExpect(status().is3xxRedirection());

        Parada actualizada = paradaRepository.findById(paradaCentro.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(actualizada.getLatitud()).isEqualTo(10.401234);
        org.assertj.core.api.Assertions.assertThat(actualizada.getLongitud()).isEqualTo(-75.501234);
        org.assertj.core.api.Assertions.assertThat(actualizada.getNombre()).isEqualTo("Parada Centro");
    }

    @Test
    void edicionDeCoordenadasEstaRestringidaAAdministradores() throws Exception {
        mockMvc.perform(get("/admin/coordenadas-paradas")
                .with(user(usuario).roles("USER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/paradas/{id}/coordenadas", paradaCentro.getId())
                .with(user(usuario).roles("USER"))
                .with(csrf())
                .param("latitud", "10.401234")
                .param("longitud", "-75.501234"))
            .andExpect(status().isForbidden());
    }

    private Parada guardarParada(String nombre, double latitud, double longitud, int orden) {
        Parada parada = new Parada();
        parada.setNombre("Parada " + nombre);
        parada.setBarrio(nombre);
        parada.setOrden(orden);
        parada.setLatitud(latitud);
        parada.setLongitud(longitud);
        parada.setRuta(ruta);
        parada = paradaRepository.saveAndFlush(parada);

        RutaParada relacion = new RutaParada();
        relacion.setParada(parada);
        relacion.setRuta(ruta);
        relacion.setOrden(orden);
        relacion.setSentido(RutaParada.Sentido.IDA);
        rutaParadaRepository.saveAndFlush(relacion);
        return parada;
    }
}
