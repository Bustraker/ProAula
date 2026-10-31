package com.proaula.aula.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrayectoFavoritoResponse(
        Long id,
        String origen,
        String destino,
        List<String> rutas,
        List<String> barrios,
        Double distanciaEstimadaKm,
        Integer tiempoEstimadoAutoMin,
        Integer paradasCount,
        LocalDateTime fechaGuardado) {
}
