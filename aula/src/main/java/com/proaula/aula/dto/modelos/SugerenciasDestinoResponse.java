package com.proaula.aula.dto.modelos;

import java.util.List;

public record SugerenciasDestinoResponse(
    List<DestinoSugeridoResponse> sugerencias,
    String metodo,
    String origenDatos
) {
}
