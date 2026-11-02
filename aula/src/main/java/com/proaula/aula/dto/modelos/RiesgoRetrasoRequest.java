package com.proaula.aula.dto.modelos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RiesgoRetrasoRequest(
    @NotBlank @Size(max = 100) String ruta,
    @NotBlank
    @Pattern(regexp = "Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo")
    String diaSemana,
    @NotNull @Min(0) @Max(23) Integer horaSalida,
    @NotNull @DecimalMin("0.01") @DecimalMax("240.0") Double duracionPlanificadaMin,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double indiceDemandaPronosticada
) {
}
