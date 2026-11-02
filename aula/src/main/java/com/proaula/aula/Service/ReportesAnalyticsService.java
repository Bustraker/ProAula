package com.proaula.aula.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.FavoritoRepository;
import com.proaula.aula.Repository.IncidenteRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Repository.ViajeRepository;
import com.proaula.aula.dto.ReportesAnalyticsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportesAnalyticsService {
    private static final DateTimeFormatter MONTH_FORMAT =
        DateTimeFormatter.ofPattern("MMM yy", Locale.forLanguageTag("es"));

    private final BusRepository busRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ParadaRepository paradaRepository;
    private final ViajeRepository viajeRepository;
    private final PasajeRepository pasajeRepository;
    private final IncidenteRepository incidenteRepository;
    private final FavoritoRepository favoritoRepository;
    private final boolean datosSinteticosConfigurados;

    public ReportesAnalyticsService(
            BusRepository busRepository,
            RutaRepository rutaRepository,
            UsuarioRepository usuarioRepository,
            ParadaRepository paradaRepository,
            ViajeRepository viajeRepository,
            PasajeRepository pasajeRepository,
            IncidenteRepository incidenteRepository,
            FavoritoRepository favoritoRepository,
            @Value("${app.synthetic-data.enabled:false}") boolean datosSinteticosConfigurados) {
        this.busRepository = busRepository;
        this.rutaRepository = rutaRepository;
        this.usuarioRepository = usuarioRepository;
        this.paradaRepository = paradaRepository;
        this.viajeRepository = viajeRepository;
        this.pasajeRepository = pasajeRepository;
        this.incidenteRepository = incidenteRepository;
        this.favoritoRepository = favoritoRepository;
        this.datosSinteticosConfigurados = datosSinteticosConfigurados;
    }

    public ReportesAnalyticsResponse obtenerIndicadores() {
        Map<String, Long> flotaPorEstado = distribucionFlota();
        Map<String, Long> flotaPorOcupacion = distribucionOcupacion();
        Map<String, Long> rutasPorEstado = distribucionRutas();
        Map<String, Long> incidentesPorEstado = distribucionIncidentesPorEstado();
        List<ReportesAnalyticsResponse.Categoria> incidentesPorTipo =
            incidenteRepository.contarPorTipo().stream()
                .map(fila -> new ReportesAnalyticsResponse.Categoria(
                    String.valueOf(fila[0]), ((Number) fila[1]).longValue()))
                .toList();
        List<ReportesAnalyticsResponse.Categoria> rutasMasConsultadas =
            viajeRepository.contarPorRuta().stream()
                .limit(6)
                .map(fila -> new ReportesAnalyticsResponse.Categoria(
                    fila[0] == null ? "Sin ruta asociada" : fila[0].toString(),
                    ((Number) fila[1]).longValue()))
                .toList();
        List<ReportesAnalyticsResponse.ActividadMensual> actividadMensual =
            construirActividadMensual();

        return new ReportesAnalyticsResponse(
            busRepository.count(),
            rutaRepository.count(),
            rutaRepository.countByEstado(Ruta.Estado.ACTIVA),
            rutaRepository.countByVerificadaTrue(),
            usuarioRepository.count(),
            paradaRepository.count(),
            paradaRepository.countByEstado(Parada.Estado.ACTIVA),
            viajeRepository.count(),
            pasajeRepository.count(),
            incidenteRepository.count(),
            incidenteRepository.countByEstadoIgnoreCase("PENDIENTE")
                + incidenteRepository.countByEstadoIgnoreCase("EN_ATENCION"),
            favoritoRepository.count(),
            datosSinteticosConfigurados,
            flotaPorEstado,
            flotaPorOcupacion,
            rutasPorEstado,
            incidentesPorEstado,
            incidentesPorTipo,
            rutasMasConsultadas,
            actividadMensual);
    }

    private Map<String, Long> distribucionFlota() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Activos", busRepository.countByEstado(Bus.Estado.ACTIVO));
        result.put("En mantenimiento", busRepository.countByEstado(Bus.Estado.MANTENIMIENTO));
        result.put("Fuera de servicio", busRepository.countByEstado(Bus.Estado.FUERA_SERVICIO));
        return result;
    }

    private Map<String, Long> distribucionOcupacion() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Baja", busRepository.countByNivelOcupacionActual(Bus.NivelOcupacion.BAJA));
        result.put("Media", busRepository.countByNivelOcupacionActual(Bus.NivelOcupacion.MEDIA));
        result.put("Alta", busRepository.countByNivelOcupacionActual(Bus.NivelOcupacion.ALTA));
        result.put("Sin dato", busRepository.countByNivelOcupacionActual(Bus.NivelOcupacion.SIN_DATO));
        return result;
    }

    private Map<String, Long> distribucionRutas() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Activas", rutaRepository.countByEstado(Ruta.Estado.ACTIVA));
        result.put("Inactivas", rutaRepository.countByEstado(Ruta.Estado.INACTIVA));
        result.put("Suspendidas", rutaRepository.countByEstado(Ruta.Estado.SUSPENDIDA));
        result.put("Verificadas", rutaRepository.countByVerificadaTrue());
        return result;
    }

    private Map<String, Long> distribucionIncidentesPorEstado() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] fila : incidenteRepository.contarPorEstado()) {
            result.put(String.valueOf(fila[0]), ((Number) fila[1]).longValue());
        }
        return result;
    }

    private List<ReportesAnalyticsResponse.ActividadMensual> construirActividadMensual() {
        YearMonth mesActual = YearMonth.now();
        YearMonth primerMes = mesActual.minusMonths(11);
        LocalDate inicio = primerMes.atDay(1);
        LocalDate fin = mesActual.plusMonths(1).atDay(1);

        Map<YearMonth, Long> viajes = new LinkedHashMap<>();
        for (Object[] fila : viajeRepository.contarPorMes(inicio, fin)) {
            viajes.put(YearMonth.of(
                ((Number) fila[0]).intValue(),
                ((Number) fila[1]).intValue()), ((Number) fila[2]).longValue());
        }

        Map<YearMonth, Long> incidentes = new LinkedHashMap<>();
        for (Object[] fila : incidenteRepository.contarPorMes(
                inicio.atStartOfDay(), fin.atStartOfDay())) {
            incidentes.put(YearMonth.of(
                ((Number) fila[0]).intValue(),
                ((Number) fila[1]).intValue()), ((Number) fila[2]).longValue());
        }

        List<ReportesAnalyticsResponse.ActividadMensual> result = new ArrayList<>(12);
        for (YearMonth mes = primerMes; !mes.isAfter(mesActual); mes = mes.plusMonths(1)) {
            result.add(new ReportesAnalyticsResponse.ActividadMensual(
                MONTH_FORMAT.format(mes.atDay(1)),
                viajes.getOrDefault(mes, 0L),
                incidentes.getOrDefault(mes, 0L)));
        }
        return result;
    }
}
