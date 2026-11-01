package com.proaula.aula.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioLoginRequest(
        @NotBlank @Size(max = 20) String username,
        @NotBlank @Size(max = 72) String password) {
}
