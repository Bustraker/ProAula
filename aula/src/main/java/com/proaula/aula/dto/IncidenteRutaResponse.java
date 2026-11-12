package com.proaula.aula.dto;

import java.time.LocalDateTime;

public record IncidenteRutaResponse(String tipo, String estado, LocalDateTime fechaReporte) {
}
