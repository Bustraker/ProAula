package com.proaula.aula.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.dto.LlegadaParadaEstimada;
import com.proaula.aula.dto.LlegadasRutaResponse;
import com.proaula.aula.dto.UbicacionBusReporteRequest;

@Service
public class LlegadaBusService {
    private static final Duration REPORT_TTL = Duration.ofSeconds(90);
    private static final Duration SAMPLE_WINDOW = Duration.ofSeconds(90);
    private static final Duration MAX_SAMPLE_GAP = Duration.ofSeconds(45);
    private static final Duration MIN_REPORT_INTERVAL = Duration.ofSeconds(5);
    private static final double MAX_ROUTE_DISTANCE_METERS = 250;
    private static final double MIN_SPEED_METERS_PER_SECOND = 0.6;
    private static final double MAX_SPEED_METERS_PER_SECOND = 22;
    private static final int MAX_REPORTS = 5000;
    private static final int MAX_ARRIVALS = 3;

    private final RutaRepository rutaRepository;
    private final RutaParadaRepository rutaParadaRepository;
    private final Clock clock;
    private final Map<ReportKey, ReportState> reports = new ConcurrentHashMap<>();

    @Autowired
    public LlegadaBusService(RutaRepository rutaRepository, RutaParadaRepository rutaParadaRepository) {
        this(rutaRepository, rutaParadaRepository, Clock.systemUTC());
    }

    LlegadaBusService(RutaRepository rutaRepository, RutaParadaRepository rutaParadaRepository, Clock clock) {
        this.rutaRepository = rutaRepository;
        this.rutaParadaRepository = rutaParadaRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LlegadasRutaResponse obtenerLlegadas(Long rutaId, RutaParada.Sentido sentido) {
        Instant ahora = clock.instant();
        eliminarReportesExpirados(ahora);
        Ruta ruta = obtenerRutaVerificada(rutaId);
        List<TrackStop> paradas = cargarParadas(rutaId, sentido);
        if (paradas.size() < 2) {
            return respuesta(ruta, sentido, "SIN_COORDENADAS",
                "La ruta necesita al menos dos paradas activas con coordenadas registradas para calcular llegadas.",
                null, 0, List.of());
        }

        List<ReportSnapshot> activos = reportesDeRuta(rutaId, sentido, ahora);
        if (activos.isEmpty()) {
            return respuesta(ruta, sentido, "SIN_REPORTES",
                "No hay reportes recientes de pasajeros en esta ruta. El tiempo de llegada no está disponible.",
                null, 0, List.of());
        }

        List<LlegadaParadaEstimada> llegadas = new ArrayList<>();
        Instant actualizadoEn = activos.stream().map(ReportSnapshot::actualizadoEn)
            .max(Comparator.naturalOrder()).orElse(null);
        for (TrackStop parada : paradas) {
            if (llegadas.size() >= MAX_ARRIVALS) {
                break;
            }
            List<ArrivalCandidate> candidatas = new ArrayList<>();
            for (ReportSnapshot reporte : activos) {
                Integer minutos = reporte.minutosHasta(parada.progresoMetros);
                if (minutos != null) {
                    candidatas.add(new ArrivalCandidate(minutos, reporte.confianza()));
                }
            }
            if (candidatas.isEmpty()) {
                continue;
            }
            ArrivalCandidate mejor = candidatas.stream()
                .min(Comparator.comparingInt(ArrivalCandidate::minutosMinimos))
                .orElseThrow();
            llegadas.add(new LlegadaParadaEstimada(
                parada.parada.getId(),
                parada.parada.getNombre(),
                parada.parada.getBarrio(),
                mejor.minutosMinimos,
                mejor.minutosMaximos,
                mejor.confianza));
        }

        if (llegadas.isEmpty()) {
            boolean movimientoValido = activos.stream().anyMatch(ReportSnapshot::tieneVelocidadValida);
            return respuesta(ruta, sentido,
                movimientoValido ? "SIN_PARADAS_PENDIENTES" : "SIN_VELOCIDAD",
                movimientoValido
                    ? "Hay movimiento reciente, pero no quedan paradas registradas por delante en este sentido."
                    : "Hay ubicación compartida, pero aún no hay suficientes reportes en movimiento para estimar llegadas.",
                actualizadoEn, activos.size(), List.of());
        }
        return respuesta(ruta, sentido, "ESTIMADO",
            "Estimación orientativa basada en reportes GPS recientes. El tráfico y los desvíos pueden cambiarla.",
            actualizadoEn, activos.size(), llegadas);
    }

    @Transactional(readOnly = true)
    public void reportarUbicacion(Long rutaId, RutaParada.Sentido sentido, String reportante,
                                  UbicacionBusReporteRequest ubicacion) {
        if (reportante == null || reportante.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para compartir tu ubicación.");
        }
        Instant ahora = clock.instant();
        eliminarReportesExpirados(ahora);
        Ruta ruta = obtenerRutaVerificada(rutaId);
        List<TrackStop> paradas = cargarParadas(rutaId, sentido);
        if (paradas.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta ruta no tiene dos paradas activas con coordenadas registradas.");
        }
        ReportKey key = new ReportKey(rutaId, sentido, reportante);
        ReportState anterior = reports.get(key);
        Projection projection = proyectar(ubicacion.latitud(), ubicacion.longitud(), paradas,
            anterior == null ? null : anterior.progresoMetros);
        if (projection.distanciaMetros > MAX_ROUTE_DISTANCE_METERS + ubicacion.precisionMetros()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "La ubicación está demasiado lejos del recorrido registrado y no se compartió.");
        }
        if (ruta.getNombre() == null || ruta.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La ruta no tiene un nombre válido.");
        }
        synchronized (reports) {
            if (reports.get(key) == null && reports.size() >= MAX_REPORTS) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de reportes está temporalmente ocupado. Inténtalo más tarde.");
            }
            reports.compute(key, (ignored, existente) -> {
                ReportState estado = existente == null ? new ReportState() : existente;
                synchronized (estado) {
                    if (estado.actualizadoEn != null
                            && Duration.between(estado.actualizadoEn, ahora).compareTo(MIN_REPORT_INTERVAL) < 0) {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Espera unos segundos antes de compartir otra actualización.");
                    }
                    estado.actualizar(projection.progresoMetros, ahora);
                    return estado;
                }
            });
        }
    }

    public void detenerReportes(String reportante) {
        if (reportante != null && !reportante.isBlank()) {
            reports.keySet().removeIf(key -> key.reportante.equals(reportante));
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void limpiarReportesExpirados() {
        eliminarReportesExpirados(clock.instant());
    }

    private Ruta obtenerRutaVerificada(Long rutaId) {
        Ruta ruta = rutaRepository.findById(rutaId).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la ruta solicitada."));
        if (!Boolean.TRUE.equals(ruta.getVerificada()) || !ruta.isOrdenBarriosConfirmado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se aceptan reportes para rutas verificadas y con sentido confirmado.");
        }
        if (ruta.getEstado() != Ruta.Estado.ACTIVA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La ruta no está activa y no puede recibir reportes de ubicación.");
        }
        return ruta;
    }

    private List<TrackStop> cargarParadas(Long rutaId, RutaParada.Sentido sentido) {
        List<RutaParada> relaciones = rutaParadaRepository
            .findByRutaIdAndSentidoOrderByOrdenAsc(rutaId, sentido);
        List<TrackStop> resultado = new ArrayList<>();
        double progreso = 0;
        TrackStop anterior = null;
        for (RutaParada relacion : relaciones) {
            Parada parada = relacion.getParada();
            if (parada == null || parada.getEstado() != Parada.Estado.ACTIVA) {
                continue;
            }
            if (parada.getLatitud() == null || parada.getLongitud() == null) {
                return List.of();
            }
            TrackStop punto = new TrackStop(parada, progreso);
            if (anterior != null) {
                progreso += distanciaMetros(anterior.parada.getLatitud(), anterior.parada.getLongitud(),
                    parada.getLatitud(), parada.getLongitud());
                punto = new TrackStop(parada, progreso);
            }
            resultado.add(punto);
            anterior = punto;
        }
        return resultado;
    }

    private List<ReportSnapshot> reportesDeRuta(Long rutaId, RutaParada.Sentido sentido, Instant ahora) {
        List<ReportSnapshot> resultado = new ArrayList<>();
        reports.forEach((key, state) -> {
            if (key.rutaId.equals(rutaId) && key.sentido == sentido) {
                synchronized (state) {
                    if (state.actualizadoEn != null
                            && Duration.between(state.actualizadoEn, ahora).compareTo(REPORT_TTL) <= 0) {
                        resultado.add(state.snapshot());
                    }
                }
            }
        });
        return resultado;
    }

    private void eliminarReportesExpirados(Instant ahora) {
        reports.entrySet().removeIf(entry -> {
            ReportState state = entry.getValue();
            synchronized (state) {
                return state.actualizadoEn == null
                    || Duration.between(state.actualizadoEn, ahora).compareTo(REPORT_TTL) > 0;
            }
        });
    }

    private Projection proyectar(double latitud, double longitud, List<TrackStop> paradas, Double progresoAnterior) {
        Projection mejor = null;
        for (int indice = 1; indice < paradas.size(); indice++) {
            TrackStop inicio = paradas.get(indice - 1);
            TrackStop fin = paradas.get(indice);
            double latMedia = (inicio.parada.getLatitud() + fin.parada.getLatitud()) / 2;
            double escalaX = 111_320 * Math.cos(Math.toRadians(latMedia));
            double dx = (fin.parada.getLongitud() - inicio.parada.getLongitud()) * escalaX;
            double dy = (fin.parada.getLatitud() - inicio.parada.getLatitud()) * 110_574;
            double px = (longitud - inicio.parada.getLongitud()) * escalaX;
            double py = (latitud - inicio.parada.getLatitud()) * 110_574;
            double largoCuadrado = dx * dx + dy * dy;
            double fraccion = largoCuadrado == 0 ? 0
                : Math.max(0, Math.min(1, (px * dx + py * dy) / largoCuadrado));
            double distancia = Math.hypot(px - fraccion * dx, py - fraccion * dy);
            double progreso = inicio.progresoMetros + fraccion * (fin.progresoMetros - inicio.progresoMetros);
            if (mejor == null || distancia < mejor.distanciaMetros - 5
                    || (progresoAnterior != null && Math.abs(distancia - mejor.distanciaMetros) <= 20
                        && Math.abs(progreso - progresoAnterior)
                            < Math.abs(mejor.progresoMetros - progresoAnterior))) {
                mejor = new Projection(progreso, distancia);
            }
        }
        return mejor;
    }

    private LlegadasRutaResponse respuesta(Ruta ruta, RutaParada.Sentido sentido, String estado, String mensaje,
                                            Instant actualizadoEn, int reportantes,
                                            List<LlegadaParadaEstimada> llegadas) {
        return new LlegadasRutaResponse(ruta.getId(), ruta.getNombre(), sentido.name(), estado, mensaje,
            actualizadoEn, reportantes, llegadas);
    }

    private static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double radio = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return radio * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record ReportKey(Long rutaId, RutaParada.Sentido sentido, String reportante) {
    }

    private record TrackStop(Parada parada, double progresoMetros) {
    }

    private record Projection(double progresoMetros, double distanciaMetros) {
    }

    private record ArrivalCandidate(int minutosMinimos, int minutosMaximos, String confianza) {
        ArrivalCandidate(int minutosMinimos, String confianza) {
            this(minutosMinimos, Math.max(minutosMinimos, (int) Math.ceil(minutosMinimos * 1.4)), confianza);
        }
    }

    private record ReportSnapshot(double progresoMetros, Instant actualizadoEn, List<ProgressSample> muestras) {
        Integer minutosHasta(double progresoParada) {
            Double velocidad = velocidadMetrosPorSegundo();
            if (velocidad == null || progresoParada <= progresoMetros) {
                return null;
            }
            return Math.max(1, (int) Math.ceil((progresoParada - progresoMetros) / velocidad / 60.0));
        }

        boolean tieneVelocidadValida() {
            return velocidadMetrosPorSegundo() != null;
        }

        private Double velocidadMetrosPorSegundo() {
            if (muestras.size() < 2) {
                return null;
            }
            ProgressSample primera = muestras.get(0);
            ProgressSample ultima = muestras.get(muestras.size() - 1);
            double segundos = Duration.between(primera.instante, ultima.instante).toMillis() / 1000.0;
            double avance = ultima.progresoMetros - primera.progresoMetros;
            double velocidad = segundos > 0 ? avance / segundos : 0;
            if (velocidad < MIN_SPEED_METERS_PER_SECOND || velocidad > MAX_SPEED_METERS_PER_SECOND) {
                return null;
            }
            return velocidad;
        }

        String confianza() {
            return muestras.size() >= 3
                    && Duration.between(muestras.get(0).instante, muestras.get(muestras.size() - 1).instante)
                        .compareTo(Duration.ofSeconds(30)) >= 0
                ? "MEDIA" : "BAJA";
        }
    }

    private static final class ReportState {
        private final Deque<ProgressSample> muestras = new ArrayDeque<>();
        private double progresoMetros;
        private Instant actualizadoEn;

        private void actualizar(double nuevoProgreso, Instant instante) {
            if (actualizadoEn != null
                    && Duration.between(actualizadoEn, instante).compareTo(MAX_SAMPLE_GAP) > 0) {
                muestras.clear();
            }
            progresoMetros = Math.max(progresoMetros, nuevoProgreso);
            actualizadoEn = instante;
            if (muestras.isEmpty() || Duration.between(muestras.getLast().instante, instante)
                    .compareTo(MIN_REPORT_INTERVAL) >= 0) {
                muestras.addLast(new ProgressSample(progresoMetros, instante));
            }
            while (muestras.size() > 6
                    || (!muestras.isEmpty()
                        && Duration.between(muestras.getFirst().instante, instante).compareTo(SAMPLE_WINDOW) > 0)) {
                muestras.removeFirst();
            }
        }

        private ReportSnapshot snapshot() {
            List<ProgressSample> copia = new ArrayList<>(muestras);
            return new ReportSnapshot(progresoMetros, actualizadoEn, copia);
        }
    }

    private record ProgressSample(double progresoMetros, Instant instante) {
    }
}
