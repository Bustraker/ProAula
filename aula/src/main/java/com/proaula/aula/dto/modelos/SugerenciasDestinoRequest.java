package com.proaula.aula.dto.modelos;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SugerenciasDestinoRequest(
    @NotBlank @Size(max = 100) String consulta,
    @NotEmpty @Size(max = 500) List<@NotNull @Valid DestinoRequest> catalogo,
    @NotNull Map<@NotBlank @Size(max = 100) String, @NotNull @Valid HistorialVisitasRequest> historial,
    @Min(1) @Max(50) int limite
) {
}
