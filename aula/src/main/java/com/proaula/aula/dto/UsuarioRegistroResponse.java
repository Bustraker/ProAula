package com.proaula.aula.dto;

import com.proaula.aula.Entity.Usuario;

public record UsuarioRegistroResponse(
    Long id,
    String username,
    String nombres,
    String apellidos,
    String email
) {
    public static UsuarioRegistroResponse from(Usuario usuario) {
        return new UsuarioRegistroResponse(
            usuario.getId(),
            usuario.getUsername(),
            usuario.getNombres(),
            usuario.getApellidos(),
            usuario.getEmail()
        );
    }
}
