package com.proaula.aula.dto;

public record AvisoFavoritoResponse(
        Long id,
        String titulo,
        String mensaje,
        boolean leido) {
}
