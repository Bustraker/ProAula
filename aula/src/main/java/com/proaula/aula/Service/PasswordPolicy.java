package com.proaula.aula.Service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final String ERROR_MESSAGE =
            "La contraseña debe tener entre 12 y 72 caracteres y no superar 72 bytes en UTF-8.";

    private PasswordPolicy() {
    }

    public static boolean isValidNewPassword(String password) {
        return password != null
                && password.length() >= 12
                && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static void validateNewPassword(String password) {
        if (!isValidNewPassword(password)) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
    }
}
