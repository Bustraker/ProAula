package com.proaula.aula.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRegistroRequest(
    @NotBlank @Size(min = 4, max = 20) String username,
    @NotBlank @Size(min = 6, max = 72) String password,
    @NotBlank String nombres,
    @NotBlank String apellidos,
    @NotBlank @Email @Size(max = 254) String email
) {
}
