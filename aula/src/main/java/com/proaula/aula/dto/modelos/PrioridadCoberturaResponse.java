package com.proaula.aula.dto.modelos;

import java.util.List;

public record PrioridadCoberturaResponse(
    String nombreModelo,
    String versionModelo,
    String metodo,
    String fuenteDatos,
    String advertencia,
    int totalBarriosAnalizados,
    int barriosPrioridadAlta,
    int barriosPrioridadMedia,
    int barriosPrioridadBaja,
    long poblacionEnBarriosSinRutaVerificada,
    List<BarrioPrioridad> barriosPrioritarios
) {
    public record BarrioPrioridad(
        String barrio,
        String localidad,
        Integer poblacionRegistrada,
        double indicePrioridad,
        String nivelPrioridad,
        int rutasActivasVerificadas,
        int paradasActivasRegistradas,
        int paradasActivasGeorreferenciadas,
        double aportePoblacion,
        double aporteFaltaRuta,
        double aporteFaltaParadasGeorreferenciadas,
        List<String> factores
    ) {
    }
}
