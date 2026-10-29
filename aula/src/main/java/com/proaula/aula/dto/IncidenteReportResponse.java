package com.proaula.aula.dto;

import java.time.LocalDateTime;

public record IncidenteReportResponse(
        Long id,
        String tipo,
        Long rutaId,
        String descripcion,
        String estado,
        LocalDateTime fechaReporte) {
}
