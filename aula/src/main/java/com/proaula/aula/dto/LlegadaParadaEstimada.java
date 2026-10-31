package com.proaula.aula.dto;

public record LlegadaParadaEstimada(
    Long paradaId,
    String parada,
    String barrio,
    Integer minutosMinimos,
    Integer minutosMaximos,
    String confianza
) {
}
