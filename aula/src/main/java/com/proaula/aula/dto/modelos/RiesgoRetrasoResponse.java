package com.proaula.aula.dto.modelos;

public record RiesgoRetrasoResponse(
    String riesgo,
    double probabilidadRetraso,
    double umbral,
    String modelo,
    String origenDatos
) {
}
