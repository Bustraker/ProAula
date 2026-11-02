package com.proaula.aula.dto;

import java.util.List;
import java.util.Map;

public record ReportesAnalyticsResponse(
    long totalBuses,
    long totalRutas,
    long rutasActivas,
    long rutasVerificadas,
    long totalUsuarios,
    long totalParadas,
    long paradasActivas,
    long totalViajesRegistrados,
    long totalPasajes,
    long totalIncidentes,
    long incidentesPorAtender,
    long totalFavoritos,
    boolean datosSinteticosConfigurados,
    Map<String, Long> flotaPorEstado,
    Map<String, Long> flotaPorOcupacion,
    Map<String, Long> rutasPorEstado,
    Map<String, Long> incidentesPorEstado,
    List<Categoria> incidentesPorTipo,
    List<Categoria> rutasMasConsultadas,
    List<ActividadMensual> actividadMensual
) {
    public record Categoria(String nombre, long cantidad) {
    }

    public record ActividadMensual(String mes, long viajesRegistrados, long incidentesReportados) {
    }
}
