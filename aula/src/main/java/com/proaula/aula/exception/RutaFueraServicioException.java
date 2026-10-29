package com.proaula.aula.exception;

public class RutaFueraServicioException extends AulaException {

    public RutaFueraServicioException(String message) {
        super(message, "RUTA_FUERA_SERVICIO");
    }
}
