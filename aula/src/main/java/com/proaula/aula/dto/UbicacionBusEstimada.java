package com.proaula.aula.dto;

import java.time.Instant;

public record UbicacionBusEstimada(
    double latitud,
    double longitud,
    Instant actualizadoEn,
    String confianza
) {
}
