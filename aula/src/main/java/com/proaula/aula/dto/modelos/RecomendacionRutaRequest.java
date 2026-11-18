package com.proaula.aula.dto.modelos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecomendacionRutaRequest(
    @NotNull @Valid OpcionRutaRequest habitual,
    @NotEmpty @Size(max = 20) List<@NotNull @Valid OpcionRutaRequest> alternativas
) {
}
