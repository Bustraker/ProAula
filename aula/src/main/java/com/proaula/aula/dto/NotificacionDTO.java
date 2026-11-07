package com.proaula.aula.dto;

public record NotificacionDTO(
        Long id,
        String titulo,
        String mensaje,
        String tipo,
        boolean leido,
        String canalEnvio,
        String fechaCreacion
) {}
