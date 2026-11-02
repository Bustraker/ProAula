package com.proaula.aula.dto.modelos;

import java.util.List;

public record RecomendacionRutaResponse(
    String rutaRecomendada,
    double tiempoGeneralizadoMin,
    String motivo,
    String metodo,
    String origenDatos,
    List<RutaOrdenadaResponse> rutasOrdenadas
) {
}
