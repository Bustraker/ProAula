package com.proaula.aula.dto.modelos;

public record RutaOrdenadaResponse(
    String codigo,
    double etaMin,
    double salidasPorHora,
    double demoraClimaMin,
    double congestionPct,
    double tiempoGeneralizadoMin
) {
}
