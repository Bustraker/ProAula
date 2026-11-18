package com.proaula.aula.Service;

import com.proaula.aula.Entity.Favorito;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.FavoritoRequest;
import com.proaula.aula.dto.FavoritoResponse;
import com.proaula.aula.exception.AulaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private ParadaRepository paradaRepository;

    @InjectMocks
    private FavoritoService favoritoService;

    @Test
    void addingExistingRouteFavoriteIsIdempotentAndReturnsItsNeighborhoods() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        Ruta ruta = new Ruta();
        ruta.setId(12L);
        ruta.setNombre("Centro");
        ruta.setVerificada(true);
        ruta.setBarrios(List.of("Centro", "Manga"));
        ruta.setBarriosOrdenados(List.of("Centro", "Manga"));
        Favorito existente = new Favorito();
        existente.setId(25L);
        existente.setTipo(Favorito.Tipo.RUTA);
        existente.setRuta(ruta);

        when(usuarioRepository.findByUsername("ana")).thenReturn(usuario);
        when(rutaRepository.findById(12L)).thenReturn(Optional.of(ruta));
        when(favoritoRepository.findByUsuario_IdAndRuta_Id(7L, 12L)).thenReturn(Optional.of(existente));

        FavoritoResponse response = favoritoService.agregar(
                new FavoritoRequest(Favorito.Tipo.RUTA, 12L, null), "ana");

        assertThat(response.id()).isEqualTo(25L);
        assertThat(response.barrios()).containsExactly("Centro", "Manga");
        verify(favoritoRepository, never()).saveAndFlush(existente);
    }

    @Test
    void deletingFavoriteRequiresThatItBelongsToTheAuthenticatedUser() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioRepository.findByUsername("ana")).thenReturn(usuario);
        when(favoritoRepository.findByIdAndUsuario_Id(25L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoritoService.eliminar(25L, "ana"))
                .isInstanceOf(AulaException.class)
                .hasMessage("No se encontró el favorito solicitado.");

        verify(favoritoRepository, never()).delete(org.mockito.ArgumentMatchers.any(Favorito.class));
    }

    @Test
    void addingRouteFavoriteRejectsUnverifiedRoute() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        Ruta ruta = new Ruta();
        ruta.setId(12L);
        ruta.setVerificada(false);
        when(usuarioRepository.findByUsername("ana")).thenReturn(usuario);
        when(rutaRepository.findById(12L)).thenReturn(Optional.of(ruta));

        assertThatThrownBy(() -> favoritoService.agregar(
                new FavoritoRequest(Favorito.Tipo.RUTA, 12L, null), "ana"))
                .isInstanceOf(AulaException.class)
                .hasMessage("La ruta seleccionada no existe o no está verificada.");

        verify(favoritoRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Favorito.class));
    }
}
