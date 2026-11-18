package com.proaula.aula.dto;

import com.proaula.aula.Entity.Favorito;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FavoritoRequest(
        @NotNull Favorito.Tipo tipo,
        @Positive Long rutaId,
        @Positive Long paradaId) {
}
