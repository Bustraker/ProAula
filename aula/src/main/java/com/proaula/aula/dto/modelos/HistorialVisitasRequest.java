package com.proaula.aula.dto.modelos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HistorialVisitasRequest(
    @NotNull @Min(0) @Max(10_000) Integer visitas7d,
    @NotNull @Min(0) @Max(50_000) Integer visitas30d,
    @NotNull @Min(0) @Max(3_650) Integer diasDesdeUltimaVisita
) {
}
