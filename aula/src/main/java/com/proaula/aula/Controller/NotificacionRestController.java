package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.NotificacionService;
import com.proaula.aula.Service.UserPresenceService;
import com.proaula.aula.dto.NotificacionDTO;
import com.proaula.aula.exception.AulaException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionRestController {

    private final NotificacionService notificacionService;
    private final UserPresenceService userPresenceService;
    private final UsuarioRepository usuarioRepository;

    public NotificacionRestController(
            NotificacionService notificacionService,
            UserPresenceService userPresenceService,
            UsuarioRepository usuarioRepository) {
        this.notificacionService = notificacionService;
        this.userPresenceService = userPresenceService;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getUsuarioAutenticado(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AulaException("Usuario no autenticado.", "UNAUTHORIZED");
        }
        Usuario usuario = usuarioRepository.findByUsername(authentication.getName());
        if (usuario == null) {
            throw new AulaException("Usuario no encontrado.", "USER_NOT_FOUND");
        }
        return usuario;
    }

    /**
     * Obtiene el listado de todas las notificaciones del usuario logueado.
     */
    @GetMapping
    public List<NotificacionDTO> listar(Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        return notificacionService.listarParaUsuario(usuario.getId());
    }

    /**
     * Heartbeat y consulta de notificaciones pendientes/no leídas.
     * Actualiza la presencia del usuario marcándolo como "dentro de la aplicación".
     */
    @GetMapping("/pendientes")
    public ResponseEntity<Map<String, Object>> obtenerPendientes(Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        List<NotificacionDTO> noLeidas = notificacionService.listarNoLeidas(usuario.getId());
        long totalNoLeidas = notificacionService.contarNoLeidas(usuario.getId());
        return ResponseEntity.ok(Map.of(
                "noLeidas", noLeidas,
                "totalNoLeidas", totalNoLeidas,
                "enLinea", true
        ));
    }

    /**
     * Endpoint ping de presencia.
     */
    @PostMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        userPresenceService.registrarActividad(usuario.getId());
        return ResponseEntity.ok(Map.of("status", "ok", "enLinea", true));
    }

    /**
     * Desconexión explícita (p. ej. al cerrar pestaña o salir de la app).
     */
    @PostMapping("/desconectar")
    public ResponseEntity<Void> desconectar(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Usuario usuario = usuarioRepository.findByUsername(authentication.getName());
            if (usuario != null) {
                userPresenceService.registrarDesconexion(usuario.getId());
            }
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Marca una notificación como leída.
     */
    @PatchMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        notificacionService.marcarComoLeida(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marca todas las notificaciones del usuario como leídas.
     */
    @PostMapping("/marcar-todas-leidas")
    public ResponseEntity<Void> marcarTodasLeidas(Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        notificacionService.marcarTodasComoLeidas(usuario.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Permite enviar una notificación de prueba para comprobar la entrega in-app (si está dentro)
     * o por correo (si está fuera), así como la síntesis de voz y el aviso audible.
     */
    @PostMapping("/test")
    public ResponseEntity<NotificacionDTO> testNotification(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        Usuario usuario = getUsuarioAutenticado(authentication);
        String titulo = (body != null && body.containsKey("titulo"))
                ? body.get("titulo")
                : "Alerta de transporte";
        String mensaje = (body != null && body.containsKey("mensaje"))
                ? body.get("mensaje")
                : "Aviso de prueba: Tu bus se encuentra próximo a la parada seleccionada.";
        String tipo = (body != null && body.containsKey("tipo"))
                ? body.get("tipo")
                : "PRUEBA";

        var n = notificacionService.enviarNotificacion(usuario, titulo, mensaje, tipo);
        return ResponseEntity.ok(new NotificacionDTO(
                n.getId(),
                n.getTitulo(),
                n.getMensaje(),
                n.getTipo(),
                Boolean.TRUE.equals(n.getLeido()),
                n.getCanalEnvio(),
                n.getFechaCreacion() != null ? n.getFechaCreacion().toString() : null
        ));
    }
}
