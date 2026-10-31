package com.proaula.aula.flows;

import com.proaula.aula.Entity.Favorito;
import com.proaula.aula.Entity.Notificacion;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.AvisoFavoritoService;
import com.proaula.aula.Service.FavoritoService;
import com.proaula.aula.dto.FavoritoRequest;
import com.proaula.aula.dto.FavoritoResponse;
import com.proaula.aula.exception.AulaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("validation")
@Transactional
class FavoriteFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FavoritoService favoritoService;

    @Autowired
    private AvisoFavoritoService avisoFavoritoService;

    @Autowired
    private FavoritoRepository favoritoRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private ParadaRepository paradaRepository;

    @Test
    void usersCanSaveRoutesAndStopsPrivatelyAndReceiveRouteAlerts() throws Exception {
        Usuario ana = usuarioRepository.saveAndFlush(usuario("ana"));
        Usuario otraPersona = usuarioRepository.saveAndFlush(usuario("otra"));
        Ruta ruta = rutaRepository.saveAndFlush(ruta());
        Parada parada = new Parada();
        parada.setNombre("Parada Centro");
        parada.setBarrio("Centro");
        parada.setRuta(ruta);
        parada = paradaRepository.saveAndFlush(parada);

        FavoritoResponse rutaFavorita = favoritoService.agregar(
                new FavoritoRequest(Favorito.Tipo.RUTA, ruta.getId(), null), ana.getUsername());
        FavoritoResponse paradaFavorita = favoritoService.agregar(
                new FavoritoRequest(Favorito.Tipo.PARADA, null, parada.getId()), ana.getUsername());

        assertThat(rutaFavorita.tipo()).isEqualTo("RUTA");
        assertThat(paradaFavorita.tipo()).isEqualTo("PARADA");
        assertThat(favoritoService.listar(ana.getUsername()))
                .extracting(FavoritoResponse::id)
                .containsExactly(paradaFavorita.id(), rutaFavorita.id());
        assertThat(favoritoService.listar(otraPersona.getUsername())).isEmpty();
        assertThat(favoritoRepository.findDistinctUsuariosByRutaFavorita(ruta.getId()))
                .containsExactly(ana);

        mockMvc.perform(get("/api/favoritos").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(content().string(containsString(ruta.getNombre())));
        mockMvc.perform(post("/api/favoritos")
                        .with(user(ana.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RUTA\",\"rutaId\":" + ruta.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rutaFavorita.id()));
        mockMvc.perform(get("/dashboard").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tus favoritos")))
                .andExpect(content().string(containsString("Ruta Centro - Manga")));
        mockMvc.perform(get("/index_3").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tus favoritos")))
                .andExpect(content().string(containsString("Ruta Centro - Manga")));
        String trayectoRequest = """
                {
                  "origen": "Centro",
                  "destino": "Manga",
                  "rutas": ["Ruta Centro - Manga"],
                  "barrios": ["Centro", "Getsemaní", "Manga"],
                  "distanciaEstimadaKm": 4.2,
                  "tiempoEstimadoAutoMin": 12,
                  "paradasCount": 5
                }
                """;
        String trayectoJson = mockMvc.perform(post("/api/favoritos/trayectos")
                        .with(user(ana.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trayectoRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("Centro"))
                .andExpect(jsonPath("$.barrios", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        long trayectoId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(trayectoJson).get("id").asLong();
        mockMvc.perform(get("/api/favoritos/trayectos")
                        .with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rutas[0]").value("Ruta Centro - Manga"));
        mockMvc.perform(get("/api/favoritos/trayectos")
                        .with(user(otraPersona.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/dashboard").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trayectos que guardaste")))
                .andExpect(content().string(containsString("Centro → Manga")))
                .andExpect(content().string(containsString("Getsemaní")));
        mockMvc.perform(get("/index_3").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Centro → Manga")));
        mockMvc.perform(delete("/api/favoritos/trayectos/{id}", trayectoId)
                        .with(user(otraPersona.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/dashboard").with(user(otraPersona.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aún no tienes favoritos")))
                .andExpect(content().string(containsString("Aún no has guardado trayectos")))
                .andExpect(content().string(containsString("Sin avisos recientes")));
        mockMvc.perform(get("/api/favoritos"))
                .andExpect(status().is3xxRedirection());

        assertThatThrownBy(() -> favoritoService.eliminar(rutaFavorita.id(), otraPersona.getUsername()))
                .isInstanceOf(AulaException.class)
                .hasMessage("No se encontró el favorito solicitado.");

        mockMvc.perform(delete("/api/favoritos/{id}", rutaFavorita.id())
                        .with(user(otraPersona.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        avisoFavoritoService.notificarCambioDeRuta(ruta);

        List<Notificacion> avisos = notificacionRepository
                .findTop5ByUsuario_IdAndTipoOrderByIdDesc(ana.getId(), AvisoFavoritoService.TIPO);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).getMensaje()).contains(ruta.getNombre());
        assertThat(notificacionRepository
                .findTop5ByUsuario_IdAndTipoOrderByIdDesc(otraPersona.getId(), AvisoFavoritoService.TIPO)).isEmpty();
        mockMvc.perform(get("/dashboard").with(user(ana.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cambios en tu ruta favorita")))
                .andExpect(content().string(containsString("fue actualizada")));
        mockMvc.perform(patch("/api/favoritos/notificaciones/{id}/leida", avisos.get(0).getId())
                        .with(user(ana.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(notificacionRepository.findById(avisos.get(0).getId()).orElseThrow().getLeido()).isTrue();
    }

    private Usuario usuario(String prefijo) {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Usuario usuario = new Usuario();
        usuario.setUsername(prefijo + sufijo);
        usuario.setPassword("hashed-password");
        usuario.setNombres("Usuario");
        usuario.setApellidos("Prueba");
        usuario.setEmail(prefijo + sufijo + "@example.test");
        return usuario;
    }

    private Ruta ruta() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta Centro - Manga");
        ruta.setVerificada(true);
        ruta.setBarrios(List.of("Centro", "Manga"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga"));
        return ruta;
    }
}
