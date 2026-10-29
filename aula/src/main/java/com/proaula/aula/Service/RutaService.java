package com.proaula.aula.Service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Horario;
import com.proaula.aula.exception.AulaException;
import com.proaula.aula.exception.RutaFueraServicioException;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.HorarioRepository;
import com.proaula.aula.Repository.AsignacionRutaBusRepository;
import com.proaula.aula.Repository.ViajeRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.IncidenteRepository;

@Service
public class RutaService {
    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private ParadaRepository paradaRepository;

    @Autowired
    private RutaParadaRepository rutaParadaRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private AsignacionRutaBusRepository asignacionRutaBusRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private PasajeRepository pasajeRepository;

    @Autowired
    private IncidenteRepository incidenteRepository;

    @Transactional(readOnly = true)
    public List<Ruta> getAllRutas() {
        List<Ruta> rutas = rutaRepository.findAll();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
    public List<Ruta> getRutasVerificadas() {
        List<Ruta> rutas = rutaRepository.findAllByVerificadaTrue();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
    public Ruta getRutaById(Long id) {
        Ruta ruta = rutaRepository.findById(id).orElse(null);
        if (ruta != null) {
            initializeDisplayCollections(ruta);
        }
        return ruta;
    }

    @Transactional(readOnly = true)
    public List<RutaParada> getParadasPorSentido(Long rutaId, RutaParada.Sentido sentido) {
        return rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(rutaId, sentido);
    }

    @Transactional(readOnly = true)
    public List<Parada> getParadasPorRuta(Long rutaId) {
        return paradaRepository.findByRuta_IdOrderByOrdenAsc(rutaId);
    }

    public Ruta saveRuta(Ruta ruta) {
        return rutaRepository.save(ruta);
    }

    @Transactional(readOnly = true)
    public LocalDateTime calcularHoraLlegadaEstimada(Long rutaId, LocalDateTime fechaHoraSalida) {
        if (rutaId == null || fechaHoraSalida == null) {
            throw new AulaException("Debe indicar la fecha y hora de salida.", "VALIDATION_ERROR");
        }

        Ruta ruta = rutaRepository.findById(rutaId)
            .orElseThrow(() -> new AulaException("La ruta seleccionada no existe.", "ROUTE_NOT_FOUND"));
        if (ruta.getEstado() != Ruta.Estado.ACTIVA) {
            throw new RutaFueraServicioException("La ruta está fuera de servicio.");
        }

        List<Horario> horariosActivos = horarioRepository.findByRutaIdAndEstadoOrderByHoraSalidaAsc(
            rutaId, Horario.Estado.ACTIVO);
        if (horariosActivos.isEmpty()) {
            throw new RutaFueraServicioException(
                "La ruta no tiene horarios de servicio activos configurados.");
        }

        Integer duracionMinutos = ruta.getTiempoEstimadoMinutos();
        if (duracionMinutos == null || duracionMinutos <= 0) {
            throw new AulaException(
                "La ruta no tiene un tiempo estimado válido para calcular la llegada.",
                "ESTIMACION_NO_DISPONIBLE");
        }

        if (!estaEnHorarioServicio(horariosActivos, fechaHoraSalida, duracionMinutos)) {
            throw new RutaFueraServicioException(
                "La ruta está fuera de servicio en la fecha y hora indicadas.");
        }

        return fechaHoraSalida.plusMinutes(duracionMinutos);
    }

    private boolean estaEnHorarioServicio(List<Horario> horarios, LocalDateTime fechaHoraSalida,
                                          int duracionMinutos) {
        for (int diasAtras = 0; diasAtras <= 1; diasAtras++) {
            LocalDate fechaServicio = fechaHoraSalida.toLocalDate().minusDays(diasAtras);
            LocalDateTime inicioServicio = null;
            LocalDateTime finServicio = null;

            for (Horario horario : horarios) {
                if (!aplicaEnDia(horario, fechaServicio.getDayOfWeek()) || horario.getHoraSalida() == null) {
                    continue;
                }

                LocalDateTime salidaProgramada = fechaServicio.atTime(horario.getHoraSalida());
                LocalDateTime llegadaProgramada = horario.getHoraLlegada() == null
                    ? salidaProgramada.plusMinutes(duracionMinutos)
                    : fechaServicio.atTime(horario.getHoraLlegada());
                if (!llegadaProgramada.isAfter(salidaProgramada)) {
                    llegadaProgramada = llegadaProgramada.plusDays(1);
                }

                if (inicioServicio == null || salidaProgramada.isBefore(inicioServicio)) {
                    inicioServicio = salidaProgramada;
                }
                if (finServicio == null || llegadaProgramada.isAfter(finServicio)) {
                    finServicio = llegadaProgramada;
                }
            }

            if (inicioServicio != null && !fechaHoraSalida.isBefore(inicioServicio)
                    && !fechaHoraSalida.isAfter(finServicio)) {
                return true;
            }
        }
        return false;
    }

    private boolean aplicaEnDia(Horario horario, DayOfWeek dia) {
        String diasSemana = horario.getDiasSemana();
        if (diasSemana != null && !diasSemana.isBlank()) {
            String diaSolicitado = nombreDia(dia);
            return List.of(diasSemana.split("[,;]")).stream()
                .map(this::normalizarDia)
                .anyMatch(diaSolicitado::equals);
        }

        if (horario.getTipoDia() == null) {
            return false;
        }
        return switch (horario.getTipoDia()) {
            case HABIL -> dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
            case SABADO -> dia == DayOfWeek.SATURDAY;
            case FESTIVO -> false;
        };
    }

    private String nombreDia(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private String normalizarDia(String dia) {
        return Normalizer.normalize(dia.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT);
    }

    @Transactional
    public void deleteRuta(Long id) {
        if (id == null || !rutaRepository.existsById(id)) {
            return;
        }
        if (hasRelatedRecords(id)) {
            throw new AulaException(
                "No se puede eliminar la ruta porque tiene buses, paradas, horarios, asignaciones, incidentes o historial asociado.",
                "RESOURCE_IN_USE");
        }
        rutaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean hasRelatedRecords(Long id) {
        return id != null && (busRepository.existsByRuta_Id(id)
                || paradaRepository.existsByRuta_Id(id)
                || rutaParadaRepository.existsByRuta_Id(id)
                || horarioRepository.existsByRuta_Id(id)
                || asignacionRutaBusRepository.existsByRuta_Id(id)
                || viajeRepository.existsByRuta_Id(id)
                || pasajeRepository.existsByRuta_Id(id)
                || incidenteRepository.existsByRuta_Id(id));
    }

    public long count() {
        return rutaRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Ruta> findTop4ByOrderByNombre() {
        List<Ruta> rutas = rutaRepository.findFirst4ByOrderByNombreAsc();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
    public long countActiveBuses() {
        return rutaRepository.findAll().stream()
            .mapToLong(ruta -> ruta.getBuses() != null ? ruta.getBuses().size() : 0)
            .sum();
    }

    public List<Ruta> findByBarrio(String barrio) {
        return rutaRepository.findAll().stream()
            .filter(ruta -> ruta.getBarrios() != null && ruta.getBarrios().contains(barrio))
            .collect(Collectors.toList());
    }

    public List<Ruta> findByNombreContaining(String nombre) {
        return rutaRepository.findAll().stream()
            .filter(ruta -> ruta.getNombre().toLowerCase().contains(nombre.toLowerCase()))
            .collect(Collectors.toList());
    }

    public Page<Ruta> findAllPaginated(Pageable pageable) {
        return rutaRepository.findAll(pageable);
    }

    public Optional<Ruta> findById(Long id) {
        return rutaRepository.findById(id);
    }

    private void initializeDisplayCollections(Ruta ruta) {
        ruta.getBarrios().size();
        ruta.getBarriosOrdenados().size();
        ruta.getBuses().size();
        ruta.getParadas().size();
    }
}