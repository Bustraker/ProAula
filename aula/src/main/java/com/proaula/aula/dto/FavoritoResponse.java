package com.proaula.aula.dto;

import java.util.List;

public record FavoritoResponse(
        Long id,
        String tipo,
        Long rutaId,
        Long paradaId,
        String nombre,
        String rutaNombre,
        String barrio,
        List<String> barrios) {
}
