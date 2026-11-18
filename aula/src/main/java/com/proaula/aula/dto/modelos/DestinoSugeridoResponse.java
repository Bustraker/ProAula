package com.proaula.aula.dto.modelos;

public record DestinoSugeridoResponse(
    String id,
    String nombre,
    String tipo,
    String barrio,
    double puntuacion,
    String motivo
) {
}
