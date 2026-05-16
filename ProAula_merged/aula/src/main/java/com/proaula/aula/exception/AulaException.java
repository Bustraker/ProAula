package com.proaula.aula.exception;

/**
 * Excepción personalizada para la aplicación Aula
 */
public class AulaException extends RuntimeException {

    private final String errorCode;

    public AulaException(String message) {
        super(message);
        this.errorCode = "AULA_ERROR";
    }

    public AulaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AulaException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AULA_ERROR";
    }

    public AulaException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}