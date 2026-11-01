package com.proaula.aula.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioRegistroRequest(
    @NotBlank @Size(min = 4, max = 20)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotBlank @Size(max = 80)
    @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$") String nombres,
    @NotBlank @Size(max = 80)
    @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$") String apellidos,
    @NotBlank @Email @Size(max = 254) String email
) {
}
