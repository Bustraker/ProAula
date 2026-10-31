package com.proaula.aula.dto;

import java.time.Instant;
import java.util.List;

public record LlegadasRutaResponse(
    Long rutaId,
    String ruta,
    String sentido,
    String estado,
    String mensaje,
    Instant actualizadoEn,
    int reportantesActivos,
    List<LlegadaParadaEstimada> llegadas
) {
}
