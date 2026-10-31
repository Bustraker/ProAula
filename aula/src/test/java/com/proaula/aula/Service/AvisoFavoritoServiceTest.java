package com.proaula.aula.Service;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvisoFavoritoServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;

    @Mock
    private NotificacionRepository notificacionRepository;

    @InjectMocks
    private AvisoFavoritoService avisoFavoritoService;

    @Test
    void notifiesUsersWhoFavoritedTheIncidentRoute() {
        Usuario usuario = new Usuario();
        Ruta ruta = new Ruta();
        ruta.setId(18L);
        ruta.setNombre("Centro - Manga");
        Incidente incidente = new Incidente();
        incidente.setRuta(ruta);
        incidente.setTitulo("Retraso reportado");
        incidente.setDescripcion("Demora en el servicio");

        when(favoritoRepository.findDistinctUsuariosByRutaFavorita(18L)).thenReturn(List.of(usuario));

        avisoFavoritoService.notificarIncidenteNuevo(incidente);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<com.proaula.aula.Entity.Notificacion>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(notificacionRepository).saveAll(captor.capture());
        var notificacion = captor.getValue().iterator().next();
        assertThat(notificacion.getUsuario()).isSameAs(usuario);
        assertThat(notificacion.getTipo()).isEqualTo(AvisoFavoritoService.TIPO);
        assertThat(notificacion.getMensaje()).contains("Centro - Manga", "Demora en el servicio");
        assertThat(notificacion.getLeido()).isFalse();
    }
}
