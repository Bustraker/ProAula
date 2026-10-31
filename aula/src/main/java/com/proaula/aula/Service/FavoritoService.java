package com.proaula.aula.Service;

import com.proaula.aula.Entity.Favorito;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.FavoritoRequest;
import com.proaula.aula.dto.FavoritoResponse;
import com.proaula.aula.exception.AulaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FavoritoService {

    private final FavoritoRepository favoritoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RutaRepository rutaRepository;
    private final ParadaRepository paradaRepository;

    public FavoritoService(FavoritoRepository favoritoRepository,
                           UsuarioRepository usuarioRepository,
                           RutaRepository rutaRepository,
                           ParadaRepository paradaRepository) {
        this.favoritoRepository = favoritoRepository;
        this.usuarioRepository = usuarioRepository;
        this.rutaRepository = rutaRepository;
        this.paradaRepository = paradaRepository;
    }

    @Transactional(readOnly = true)
    public List<FavoritoResponse> listar(String username) {
        Usuario usuario = usuarioActual(username);
        return favoritoRepository.findByUsuario_IdOrderByIdDesc(usuario.getId()).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional
    public FavoritoResponse agregar(FavoritoRequest solicitud, String username) {
        Usuario usuario = usuarioActual(username);
        validarObjetivo(solicitud);

        Favorito existente;
        if (solicitud.tipo() == Favorito.Tipo.RUTA) {
            Ruta ruta = rutaRepository.findById(solicitud.rutaId())
                    .filter(rutaEncontrada -> Boolean.TRUE.equals(rutaEncontrada.getVerificada()))
                    .orElseThrow(() -> new AulaException(
                            "La ruta seleccionada no existe o no está verificada.", "ROUTE_NOT_FOUND"));
            existente = favoritoRepository.findByUsuario_IdAndRuta_Id(usuario.getId(), ruta.getId())
                    .orElse(null);
            if (existente != null) {
                return respuesta(existente);
            }

            Favorito favorito = new Favorito();
            favorito.setUsuario(usuario);
            favorito.setTipo(Favorito.Tipo.RUTA);
            favorito.setRuta(ruta);
            return respuesta(favoritoRepository.saveAndFlush(favorito));
        }

        Parada parada = paradaRepository.findById(solicitud.paradaId())
                .filter(paradaEncontrada -> paradaEncontrada.getRuta() != null
                        && Boolean.TRUE.equals(paradaEncontrada.getRuta().getVerificada()))
                .orElseThrow(() -> new AulaException(
                        "La parada seleccionada no existe o su ruta no está verificada.", "STOP_NOT_FOUND"));
        existente = favoritoRepository.findByUsuario_IdAndParada_Id(usuario.getId(), parada.getId())
                .orElse(null);
        if (existente != null) {
            return respuesta(existente);
        }

        Favorito favorito = new Favorito();
        favorito.setUsuario(usuario);
        favorito.setTipo(Favorito.Tipo.PARADA);
        favorito.setParada(parada);
        return respuesta(favoritoRepository.saveAndFlush(favorito));
    }

    @Transactional
    public void eliminar(Long favoritoId, String username) {
        if (favoritoId == null || favoritoId <= 0) {
            throw new AulaException("El identificador del favorito no es válido.", "VALIDATION_ERROR");
        }
        Usuario usuario = usuarioActual(username);
        Favorito favorito = favoritoRepository.findByIdAndUsuario_Id(favoritoId, usuario.getId())
                .orElseThrow(() -> new AulaException(
                        "No se encontró el favorito solicitado.", "FAVORITE_NOT_FOUND"));
        favoritoRepository.delete(favorito);
    }

    private Usuario usuarioActual(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new AulaException("No se encontró el usuario autenticado.", "USER_NOT_FOUND");
        }
        return usuario;
    }

    private void validarObjetivo(FavoritoRequest solicitud) {
        if (solicitud == null || solicitud.tipo() == null) {
            throw new AulaException("Indica si el favorito es una ruta o una parada.", "VALIDATION_ERROR");
        }
        boolean rutaCompleta = solicitud.rutaId() != null && solicitud.paradaId() == null;
        boolean paradaCompleta = solicitud.paradaId() != null && solicitud.rutaId() == null;
        if ((solicitud.tipo() == Favorito.Tipo.RUTA && !rutaCompleta)
                || (solicitud.tipo() == Favorito.Tipo.PARADA && !paradaCompleta)) {
            throw new AulaException("El tipo y el identificador del favorito no coinciden.", "VALIDATION_ERROR");
        }
    }

    private FavoritoResponse respuesta(Favorito favorito) {
        if (favorito.getTipo() == Favorito.Tipo.RUTA) {
            Ruta ruta = favorito.getRuta();
            return new FavoritoResponse(
                    favorito.getId(),
                    favorito.getTipo().name(),
                    ruta.getId(),
                    null,
                    ruta.getNombre(),
                    ruta.getNombre(),
                    null,
                    barriosRuta(ruta));
        }

        Parada parada = favorito.getParada();
        Ruta ruta = parada.getRuta();
        String barrio = parada.getBarrio();
        return new FavoritoResponse(
                favorito.getId(),
                favorito.getTipo().name(),
                ruta == null ? null : ruta.getId(),
                parada.getId(),
                parada.getNombre(),
                ruta == null ? null : ruta.getNombre(),
                barrio,
                barrio == null || barrio.isBlank() ? List.of() : List.of(barrio));
    }

    private List<String> barriosRuta(Ruta ruta) {
        if (!ruta.isOrdenBarriosConfirmado()) {
            return List.of();
        }
        List<String> barrios = ruta.getBarriosOrdenados();
        Set<String> unicos = new LinkedHashSet<>();
        if (barrios != null) {
            barrios.stream()
                    .filter(barrio -> barrio != null && !barrio.isBlank())
                    .forEach(unicos::add);
        }
        return List.copyOf(unicos);
    }
}
