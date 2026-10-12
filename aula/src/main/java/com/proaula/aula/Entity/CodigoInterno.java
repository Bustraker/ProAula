package com.proaula.aula.Entity;

import java.security.SecureRandom;

final class CodigoInterno {

    private static final char[] CARACTERES = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoInterno() {
    }

    static String generar(String prefijo) {
        StringBuilder codigo = new StringBuilder(prefijo);
        while (codigo.length() < 20) {
            codigo.append(CARACTERES[ALEATORIO.nextInt(CARACTERES.length)]);
        }
        return codigo.toString();
    }
}
