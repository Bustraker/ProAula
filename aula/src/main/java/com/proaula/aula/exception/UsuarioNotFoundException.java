package com.proaula.aula.exception;

/**
 * Excepción para cuando un usuario no es encontrado
 */
public class UsuarioNotFoundException extends AulaException {

    public UsuarioNotFoundException(String username) {
        super("Usuario no encontrado: " + username, "USER_NOT_FOUND");
    }

    public UsuarioNotFoundException(Long userId) {
        super("Usuario no encontrado con ID: " + userId, "USER_NOT_FOUND");
    }
}