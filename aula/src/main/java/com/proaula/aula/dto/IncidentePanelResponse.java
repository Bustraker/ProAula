package com.proaula.aula.dto;

import java.time.LocalDateTime;

public record IncidentePanelResponse(
        Long id,
        String ruta,
        String tipo,
        String descripcion,
        LocalDateTime fecha,
        String estado) {
}
