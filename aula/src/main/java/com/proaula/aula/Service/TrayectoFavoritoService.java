package com.proaula.aula.Service;

import com.proaula.aula.Entity.TrayectoFavorito;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.TrayectoFavoritoRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.TrayectoFavoritoRequest;
import com.proaula.aula.dto.TrayectoFavoritoResponse;
import com.proaula.aula.exception.AulaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class TrayectoFavoritoService {

    private final TrayectoFavoritoRepository trayectoRepository;
    private final UsuarioRepository usuarioRepository;

    public TrayectoFavoritoService(TrayectoFavoritoRepository trayectoRepository,
                                   UsuarioRepository usuarioRepository) {
        this.trayectoRepository = trayectoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<TrayectoFavoritoResponse> listar(String username) {
        Usuario usuario = usuarioActual(username);
        return trayectoRepository.findByUsuario_IdOrderByFechaGuardadoDesc(usuario.getId()).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional
    public TrayectoFavoritoResponse agregar(TrayectoFavoritoRequest request, String username) {
        Usuario usuario = usuarioActual(username);
        validar(request);

        TrayectoFavorito trayecto = new TrayectoFavorito();
        trayecto.setUsuario(usuario);
        trayecto.setOrigen(request.origen().trim());
        trayecto.setDestino(request.destino().trim());
        trayecto.setRutasNombres(String.join("\n", limpiar(request.rutas())));
        trayecto.setBarriosRecorridos(String.join("\n", limpiar(request.barrios())));
        trayecto.setDistanciaEstimadaKm(request.distanciaEstimadaKm());
        trayecto.setTiempoEstimadoAutoMin(request.tiempoEstimadoAutoMin());
        trayecto.setParadasCount(request.paradasCount() == null ? 0 : request.paradasCount());
        return respuesta(trayectoRepository.saveAndFlush(trayecto));
    }

    @Transactional
    public void eliminar(Long trayectoId, String username) {
        if (trayectoId == null || trayectoId <= 0) {
            throw new AulaException("El identificador del trayecto no es válido.", "VALIDATION_ERROR");
        }
        Usuario usuario = usuarioActual(username);
        TrayectoFavorito trayecto = trayectoRepository.findByIdAndUsuario_Id(trayectoId, usuario.getId())
                .orElseThrow(() -> new AulaException(
                        "No se encontró el trayecto favorito solicitado.", "FAVORITE_NOT_FOUND"));
        trayectoRepository.delete(trayecto);
    }

    private Usuario usuarioActual(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new AulaException("No se encontró el usuario autenticado.", "USER_NOT_FOUND");
        }
        return usuario;
    }

    private void validar(TrayectoFavoritoRequest request) {
        if (request == null || request.origen() == null || request.origen().isBlank()
                || request.origen().length() > 255 || request.destino() == null || request.destino().isBlank()
                || request.destino().length() > 255 || request.rutas() == null || request.barrios() == null
                || request.rutas().isEmpty() || request.rutas().size() > 5
                || request.barrios().isEmpty() || request.barrios().size() > 30
                || request.rutas().stream().anyMatch(ruta -> textoInvalido(ruta, 150))
                || request.barrios().stream().anyMatch(barrio -> textoInvalido(barrio, 255))
                || (request.distanciaEstimadaKm() != null
                    && (!Double.isFinite(request.distanciaEstimadaKm())
                        || request.distanciaEstimadaKm() < 0 || request.distanciaEstimadaKm() > 1000))
                || (request.tiempoEstimadoAutoMin() != null
                    && (request.tiempoEstimadoAutoMin() < 0 || request.tiempoEstimadoAutoMin() > 1440))
                || (request.paradasCount() != null && (request.paradasCount() < 0 || request.paradasCount() > 200))) {
            throw new AulaException("El resumen del trayecto no tiene un formato válido.", "VALIDATION_ERROR");
        }
    }

    private boolean textoInvalido(String valor, int longitudMaxima) {
        return valor == null || valor.isBlank() || valor.length() > longitudMaxima
                || valor.contains("\n") || valor.contains("\r");
    }

    private List<String> limpiar(List<String> valores) {
        return valores.stream().map(String::trim).toList();
    }

    private TrayectoFavoritoResponse respuesta(TrayectoFavorito trayecto) {
        return new TrayectoFavoritoResponse(
                trayecto.getId(),
                trayecto.getOrigen(),
                trayecto.getDestino(),
                separar(trayecto.getRutasNombres()),
                separar(trayecto.getBarriosRecorridos()),
                trayecto.getDistanciaEstimadaKm(),
                trayecto.getTiempoEstimadoAutoMin(),
                trayecto.getParadasCount(),
                trayecto.getFechaGuardado());
    }

    private List<String> separar(String valores) {
        if (valores == null || valores.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valores.split("\n")).toList();
    }
}
