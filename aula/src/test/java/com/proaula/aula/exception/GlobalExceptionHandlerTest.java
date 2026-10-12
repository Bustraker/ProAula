package com.proaula.aula.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {
    @Test
    void apiErrorsReturnStructuredJsonWithoutDevelopmentDetails() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(environment);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/buses");

        Object result = handler.handleGenericException(
            new IllegalStateException("internal detail"), new ExtendedModelMap(), request);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error")).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("path")).isEqualTo("/api/buses");
        assertThat(body.containsKey("stackTrace")).isFalse();
        assertThat(body.get("message")).isNotEqualTo("internal detail");
    }

    @Test
    void browserErrorsStillReturnHtmlViewNames() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(environment);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        ExtendedModelMap model = new ExtendedModelMap();

        Object result = handler.handleGenericException(
            new IllegalStateException("internal detail"), model, request);

        assertThat(result).isEqualTo("error/500");
        assertThat(model.get("errorCode")).isEqualTo("INTERNAL_ERROR");
        assertThat(model.get("errorMessage")).isEqualTo("Ha ocurrido un error interno en el servidor");
    }
}
