package com.proaula.aula.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(UsuarioNotFoundException.class)
    public Object handleUsuarioNotFoundException(UsuarioNotFoundException ex, Model model,
                                                HttpServletRequest request, HttpServletResponse response) {
        logger.warn("UsuarioNotFoundException: {}", ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, ex.getErrorCode(), "Usuario no encontrado",
            "error/404", model, request, response, Map.of());
    }

    @ExceptionHandler(AulaException.class)
    public Object handleAulaException(AulaException ex, Model model, HttpServletRequest request,
                                      HttpServletResponse response) {
        logger.warn("AulaException: {}", ex.getMessage());
        boolean conflict = "RESOURCE_IN_USE".equals(ex.getErrorCode())
                || "EMAIL_ALREADY_EXISTS".equals(ex.getErrorCode());
        HttpStatus status = "INCIDENT_NOT_FOUND".equals(ex.getErrorCode())
                ? HttpStatus.NOT_FOUND
                : conflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return respond(status, ex.getErrorCode(), ex.getMessage(),
            conflict ? "error/409" : status == HttpStatus.NOT_FOUND ? "error/404" : "error/400",
            model, request, response, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException ex, Model model,
                                            HttpServletRequest request, HttpServletResponse response) {
        logger.warn("AccessDeniedException: {}", ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "No tienes permisos para acceder a este recurso", "error/403", model, request, response, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public Object handleAuthenticationException(AuthenticationException ex, Model model,
                                                HttpServletRequest request, HttpServletResponse response) {
        logger.warn("AuthenticationException: {}", ex.getMessage());
        return respond(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Error de autenticación",
            "error/401", model, request, response, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationException(MethodArgumentNotValidException ex, Model model,
                                            HttpServletRequest request, HttpServletResponse response) {
        logger.warn("ValidationException: {}", ex.getMessage());
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Datos de entrada inválidos",
            "error/400", model, request, response, Map.of("fieldErrors", errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, Model model,
                                     HttpServletRequest request, HttpServletResponse response) {
        logger.warn("Invalid request value for '{}': {}", ex.getName(), ex.getValue());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "El valor indicado para '" + ex.getName() + "' no es válido.",
            "error/400", model, request, response, Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public Object handleDataAccessException(DataAccessException ex, Model model,
                                            HttpServletRequest request, HttpServletResponse response) {
        logger.error("DataAccessException: {}", ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
            "Error de conexión con la base de datos", "error/500", model, request, response, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, Model model, HttpServletRequest request,
                                         HttpServletResponse response) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        Map<String, Object> details = new LinkedHashMap<>();
        if (isDevelopment()) {
            details.put("errorDetails", ex.getMessage());
            details.put("stackTrace", getStackTraceAsString(ex));
        }
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Ha ocurrido un error interno en el servidor", "error/500", model, request, response, details);
    }

    private Object respond(HttpStatus status, String errorCode, String errorMessage, String view,
                           Model model, HttpServletRequest request,
                           HttpServletResponse response,
                           Map<String, ?> additionalAttributes) {
        LocalDateTime timestamp = LocalDateTime.now();
        if (isApiRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", timestamp.toString());
            body.put("status", status.value());
            body.put("error", errorCode);
            body.put("message", errorMessage);
            body.put("path", request.getRequestURI());
            Object fieldErrors = additionalAttributes.get("fieldErrors");
            if (fieldErrors != null) {
                body.put("fieldErrors", fieldErrors);
            }
            return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        }

        response.setStatus(status.value());
        model.addAttribute("error", true);
        model.addAttribute("errorCode", errorCode);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("timestamp", timestamp);
        model.addAttribute("path", request.getRequestURI());
        additionalAttributes.forEach(model::addAttribute);
        return view;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private boolean isDevelopment() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile) || "development".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private String getStackTraceAsString(Exception ex) {
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            stackTrace.append(element).append('\n');
        }
        return stackTrace.toString();
    }
}
