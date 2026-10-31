package com.proaula.aula.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrayectoFavoritoRequest(
        @NotBlank @Size(max = 255) String origen,
        @NotBlank @Size(max = 255) String destino,
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 150) String> rutas,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 255) String> barrios,
        @DecimalMin("0.0") @DecimalMax("1000.0") Double distanciaEstimadaKm,
        @Min(0) @Max(1440) Integer tiempoEstimadoAutoMin,
        @Min(0) @Max(200) Integer paradasCount) {
}
