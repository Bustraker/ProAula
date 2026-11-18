package com.proaula.aula.dto;

import java.math.BigDecimal;

public record BarrioContextoResponse(
    String nombreBarrio,
    String codigoOficial,
    String localidadOficial,
    Integer unidadComunalGestion,
    BigDecimal areaHectareas,
    Integer poblacion,
    Integer hogares,
    Integer viviendas,
    BigDecimal densidadHabitantesHa
) {
}
