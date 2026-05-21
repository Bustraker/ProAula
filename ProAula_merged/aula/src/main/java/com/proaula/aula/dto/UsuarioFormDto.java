package com.proaula.aula.dto;

import lombok.Data;

@Data
public class UsuarioFormDto {
    private Long id;
    private String username;
    private String password;
    private String provider = "LOCAL";
    private String providerId;
    private String role = "ROLE_USER";
    private String nombres;
    private String apellidos;
    private String email;
}
