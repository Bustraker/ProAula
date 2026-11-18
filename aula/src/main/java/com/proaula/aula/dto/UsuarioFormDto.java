package com.proaula.aula.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioFormDto {
    private Long id;
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    @Size(min = 4, max = 20, message = "El usuario debe tener entre 4 y 20 caracteres.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "El usuario solo puede contener letras, números, punto, guion y guion bajo.")
    private String username;
    @NotBlank(message = "La contraseña es obligatoria.")
    @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres.")
    private String password;
    private String provider = "LOCAL";
    private String providerId;
    private String role = "ROLE_USER";
    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 80, message = "Los nombres no pueden superar 80 caracteres.")
    @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$", message = "Los nombres contienen caracteres no permitidos.")
    private String nombres;
    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 80, message = "Los apellidos no pueden superar 80 caracteres.")
    @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$", message = "Los apellidos contienen caracteres no permitidos.")
    private String apellidos;
    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "Ingresa un correo electrónico válido.")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres.")
    private String email;
}
