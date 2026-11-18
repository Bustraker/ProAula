package com.proaula.aula.dto.modelos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DestinoRequest(
    @NotBlank @Size(max = 100) String id,
    @NotBlank @Size(max = 255) String nombre,
    @NotBlank @Size(max = 50) String tipo,
    @Size(max = 255) String barrio
) {
}
