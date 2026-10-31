package com.proaula.aula.Service;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Notificacion;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.dto.AvisoFavoritoResponse;
import com.proaula.aula.exception.AulaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvisoFavoritoService {

    public static final String TIPO = "AVISO_RUTA_FAVORITA";

    private final FavoritoRepository favoritoRepository;
    private final NotificacionRepository notificacionRepository;

    public AvisoFavoritoService(FavoritoRepository favoritoRepository,
                                NotificacionRepository notificacionRepository) {
        this.favoritoRepository = favoritoRepository;
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional
    public void notificarIncidenteNuevo(Incidente incidente) {
        if (incidente.getRuta() == null) {
            return;
        }
        String titulo = incidente.getTitulo() == null ? "Incidente reportado" : incidente.getTitulo();
        String mensaje = "Se reportó un incidente en " + incidente.getRuta().getNombre()
                + ": " + incidente.getDescripcion();
        notificarUsuariosDeRuta(incidente.getRuta(), titulo, mensaje);
    }

    @Transactional
    public void notificarCambioDeEstado(Incidente incidente) {
        if (incidente.getRuta() == null) {
            return;
        }
        String titulo = "Actualización de incidente en tu ruta favorita";
        String mensaje = "El incidente \"" + incidente.getTitulo() + "\" en "
                + incidente.getRuta().getNombre() + " cambió al estado " + incidente.getEstado() + ".";
        notificarUsuariosDeRuta(incidente.getRuta(), titulo, mensaje);
    }

    @Transactional
    public void notificarCambioDeRuta(Ruta ruta) {
        notificarUsuariosDeRuta(
                ruta,
                "Cambios en tu ruta favorita",
                "La información de " + ruta.getNombre()
                        + " fue actualizada. Revisa sus barrios y paradas antes de viajar.");
    }

    @Transactional(readOnly = true)
    public List<AvisoFavoritoResponse> listar(Long usuarioId) {
        return notificacionRepository.findTop5ByUsuario_IdAndTipoOrderByIdDesc(usuarioId, TIPO).stream()
                .map(notificacion -> new AvisoFavoritoResponse(
                        notificacion.getId(),
                        notificacion.getTitulo(),
                        notificacion.getMensaje(),
                        Boolean.TRUE.equals(notificacion.getLeido())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidos(Long usuarioId) {
        return notificacionRepository.countByUsuario_IdAndTipoAndLeidoFalse(usuarioId, TIPO);
    }

    @Transactional
    public void marcarLeida(Long notificacionId, Long usuarioId) {
        if (notificacionId == null || notificacionId <= 0) {
            throw new AulaException("El identificador del aviso no es válido.", "VALIDATION_ERROR");
        }
        Notificacion notificacion = notificacionRepository
                .findByIdAndUsuario_IdAndTipo(notificacionId, usuarioId, TIPO)
                .orElseThrow(() -> new AulaException("No se encontró el aviso solicitado.", "NOTIFICATION_NOT_FOUND"));
        if (!Boolean.TRUE.equals(notificacion.getLeido())) {
            notificacion.setLeido(true);
            notificacion.setFechaLeido(java.time.LocalDateTime.now());
            notificacionRepository.save(notificacion);
        }
    }

    private void notificarUsuariosDeRuta(Ruta ruta, String titulo, String mensaje) {
        List<Notificacion> avisos = favoritoRepository
                .findDistinctUsuariosByRutaFavorita(ruta.getId()).stream()
                .map(usuario -> crearNotificacion(usuario, titulo, mensaje))
                .toList();
        if (!avisos.isEmpty()) {
            notificacionRepository.saveAll(avisos);
        }
    }

    private Notificacion crearNotificacion(Usuario usuario, String titulo, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacion.setTipo(TIPO);
        notificacion.setLeido(false);
        return notificacion;
    }
}
