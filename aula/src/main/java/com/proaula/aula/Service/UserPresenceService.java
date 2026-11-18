package com.proaula.aula.Service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de presencia de usuarios para determinar si un usuario se encuentra
 * activo dentro de la aplicación o si está fuera de ella.
 */
@Service
public class UserPresenceService {

    // Umbral de actividad: 60 segundos sin ping/interacción considera al usuario "fuera de la aplicación".
    private static final long ONLINE_THRESHOLD_MS = 60_000L;

    private final Map<Long, Long> lastActiveTimeByUserId = new ConcurrentHashMap<>();

    public void registrarActividad(Long usuarioId) {
        if (usuarioId != null) {
            lastActiveTimeByUserId.put(usuarioId, System.currentTimeMillis());
        }
    }

    public void registrarDesconexion(Long usuarioId) {
        if (usuarioId != null) {
            lastActiveTimeByUserId.remove(usuarioId);
        }
    }

    public boolean estaEnLinea(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        Long lastSeen = lastActiveTimeByUserId.get(usuarioId);
        if (lastSeen == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastSeen) <= ONLINE_THRESHOLD_MS;
    }
}
