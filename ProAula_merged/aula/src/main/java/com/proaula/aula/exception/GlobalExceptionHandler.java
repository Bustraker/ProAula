package com.proaula.aula.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para la aplicación
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja excepciones de la aplicación personalizadas
     */
    @ExceptionHandler(AulaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleAulaException(AulaException ex, Model model, HttpServletRequest request) {
        logger.error("AulaException: {}", ex.getMessage(), ex);

        model.addAttribute("error", true);
        model.addAttribute("errorCode", ex.getErrorCode());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/400";
    }

    /**
     * Maneja excepciones de usuario no encontrado
     */
    @ExceptionHandler(UsuarioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUsuarioNotFoundException(UsuarioNotFoundException ex, Model model, HttpServletRequest request) {
        logger.warn("UsuarioNotFoundException: {}", ex.getMessage());

        model.addAttribute("error", true);
        model.addAttribute("errorCode", ex.getErrorCode());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/404";
    }

    /**
     * Maneja excepciones de acceso denegado (Spring Security)
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model, HttpServletRequest request) {
        logger.warn("AccessDeniedException: {}", ex.getMessage());

        model.addAttribute("error", true);
        model.addAttribute("errorCode", "ACCESS_DENIED");
        model.addAttribute("errorMessage", "No tienes permisos para acceder a este recurso");
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/403";
    }

    /**
     * Maneja excepciones de autenticación (Spring Security)
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAuthenticationException(AuthenticationException ex, Model model, HttpServletRequest request) {
        logger.warn("AuthenticationException: {}", ex.getMessage());

        model.addAttribute("error", true);
        model.addAttribute("errorCode", "AUTHENTICATION_FAILED");
        model.addAttribute("errorMessage", "Error de autenticación");
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/401";
    }

    /**
     * Maneja excepciones de validación
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException ex, Model model, HttpServletRequest request) {
        logger.warn("ValidationException: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        model.addAttribute("error", true);
        model.addAttribute("errorCode", "VALIDATION_ERROR");
        model.addAttribute("errorMessage", "Datos de entrada inválidos");
        model.addAttribute("fieldErrors", errors);
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/400";
    }

    /**
     * Maneja excepciones generales
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model, HttpServletRequest request) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);

        model.addAttribute("error", true);
        model.addAttribute("errorCode", "INTERNAL_ERROR");
        model.addAttribute("errorMessage", "Ha ocurrido un error interno en el servidor");
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        // En desarrollo, mostrar detalles del error
        if (isDevelopment()) {
            model.addAttribute("errorDetails", ex.getMessage());
            model.addAttribute("stackTrace", getStackTraceAsString(ex));
        }

        return "error/500";
    }

    /**
     * Maneja excepciones de base de datos
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleDataAccessException(org.springframework.dao.DataAccessException ex, Model model, HttpServletRequest request) {
        logger.error("DataAccessException: {}", ex.getMessage(), ex);

        model.addAttribute("error", true);
        model.addAttribute("errorCode", "DATABASE_ERROR");
        model.addAttribute("errorMessage", "Error de conexión con la base de datos");
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("path", request.getRequestURI());

        return "error/500";
    }

    private boolean isDevelopment() {
        String profile = System.getProperty("spring.profiles.active", "");
        return "dev".equals(profile) || "development".equals(profile) || profile.isEmpty();
    }

    private String getStackTraceAsString(Exception ex) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}