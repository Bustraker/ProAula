package com.proaula.aula.dto.modelos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpcionRutaRequest(
    @NotBlank @Size(max = 100) String codigo,
    @NotNull @DecimalMin("0.01") @DecimalMax("600.0") Double etaMin,
    @NotNull @DecimalMin("0.01") @DecimalMax("60.0") Double salidasPorHora,
    @NotNull @DecimalMin("0.0") @DecimalMax("180.0") Double demoraClimaMin,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double congestionPct
) {
}
