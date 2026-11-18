package com.proaula.aula.Service;

import com.proaula.aula.Entity.Notificacion;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.dto.NotificacionDTO;
import com.proaula.aula.exception.AulaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificacionRepository;
    private final UserPresenceService userPresenceService;
    private final NotificationEmailService notificationEmailService;

    public NotificacionService(
            NotificacionRepository notificacionRepository,
            UserPresenceService userPresenceService,
            NotificationEmailService notificationEmailService) {
        this.notificacionRepository = notificacionRepository;
        this.userPresenceService = userPresenceService;
        this.notificationEmailService = notificationEmailService;
    }

    /**
     * Envía una notificación al usuario logueado.
     * Si el usuario se encuentra dentro de la aplicación (en línea), se almacena y se le entrega directamente en la app.
     * Si el usuario está fuera de la aplicación, se despacha además al correo electrónico registrado.
     */
    @Transactional
    public Notificacion enviarNotificacion(Usuario usuario, String titulo, String mensaje, String tipo) {
        if (usuario == null) {
            return null;
        }

        boolean estaEnLinea = userPresenceService.estaEnLinea(usuario.getId());
        String canal = estaEnLinea ? "IN_APP" : "EMAIL";

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTitulo(titulo != null ? titulo : "Aviso importante");
        notificacion.setMensaje(mensaje != null ? mensaje : "");
        notificacion.setTipo(tipo != null ? tipo : "GENERAL");
        notificacion.setLeido(false);
        notificacion.setCanalEnvio(canal);
        notificacion.setFechaCreacion(LocalDateTime.now());

        Notificacion guardada = notificacionRepository.save(notificacion);

        if (!estaEnLinea) {
            log.info("Usuario {} está fuera de la app (offline). Enviando notificación a su correo {}",
                    usuario.getUsername(), usuario.getEmail());
            notificationEmailService.enviarNotificacionEmail(
                    usuario.getEmail(),
                    titulo,
                    mensaje,
                    tipo,
                    usuario.getNombres() != null ? usuario.getNombres() : usuario.getUsername()
            );
        } else {
            log.info("Usuario {} se encuentra dentro de la app (en línea). Notificación disponible para entrega directa.",
                    usuario.getUsername());
        }

        return guardada;
    }

    @Transactional(readOnly = true)
    public List<NotificacionDTO> listarParaUsuario(Long usuarioId) {
        userPresenceService.registrarActividad(usuarioId);
        return notificacionRepository.findByUsuarioIdOrderByIdDesc(usuarioId).stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificacionDTO> listarNoLeidas(Long usuarioId) {
        userPresenceService.registrarActividad(usuarioId);
        return notificacionRepository.findByUsuarioIdAndLeidoFalseOrderByIdDesc(usuarioId).stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(Long usuarioId) {
        return notificacionRepository.countByUsuario_IdAndLeidoFalse(usuarioId);
    }

    @Transactional
    public void marcarComoLeida(Long notificacionId, Long usuarioId) {
        if (notificacionId == null || notificacionId <= 0) {
            throw new AulaException("El identificador de la notificación no es válido.", "VALIDATION_ERROR");
        }
        Notificacion notificacion = notificacionRepository.findByIdAndUsuario_Id(notificacionId, usuarioId)
                .orElseThrow(() -> new AulaException("Notificación no encontrada.", "NOTIFICATION_NOT_FOUND"));
        if (!Boolean.TRUE.equals(notificacion.getLeido())) {
            notificacion.setLeido(true);
            notificacion.setFechaLeido(LocalDateTime.now());
            notificacionRepository.save(notificacion);
        }
    }

    @Transactional
    public void marcarTodasComoLeidas(Long usuarioId) {
        List<Notificacion> noLeidas = notificacionRepository.findByUsuarioIdAndLeidoFalseOrderByIdDesc(usuarioId);
        LocalDateTime ahora = LocalDateTime.now();
        for (Notificacion n : noLeidas) {
            n.setLeido(true);
            n.setFechaLeido(ahora);
        }
        if (!noLeidas.isEmpty()) {
            notificacionRepository.saveAll(noLeidas);
        }
    }

    private NotificacionDTO convertirADTO(Notificacion n) {
        return new NotificacionDTO(
                n.getId(),
                n.getTitulo(),
                n.getMensaje(),
                n.getTipo(),
                Boolean.TRUE.equals(n.getLeido()),
                n.getCanalEnvio(),
                n.getFechaCreacion() != null ? n.getFechaCreacion().toString() : null
        );
    }
}
