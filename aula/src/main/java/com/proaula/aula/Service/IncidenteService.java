package com.proaula.aula.Service;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.IncidenteRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.IncidentePanelResponse;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.dto.IncidenteRutaResponse;
import com.proaula.aula.exception.AulaException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IncidenteService {

    private static final Set<String> ESTADOS_PERMITIDOS =
            Set.of("PENDIENTE", "EN_ATENCION", "RESUELTO", "DESCARTADO");
    private static final List<String> ESTADOS_ACTIVOS = List.of("PENDIENTE", "EN_ATENCION");

    private final IncidenteRepository incidenteRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvisoFavoritoService avisoFavoritoService;

    public IncidenteService(IncidenteRepository incidenteRepository,
                            RutaRepository rutaRepository,
                            UsuarioRepository usuarioRepository,
                            AvisoFavoritoService avisoFavoritoService) {
        this.incidenteRepository = incidenteRepository;
        this.rutaRepository = rutaRepository;
        this.usuarioRepository = usuarioRepository;
        this.avisoFavoritoService = avisoFavoritoService;
    }

    @Transactional(readOnly = true)
    public List<Ruta> getRutas() {
        return rutaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<IncidentePanelResponse> listar(String tipo, String ruta, String estado, Pageable pageable) {
        String tipoFiltro = normalizarFiltro(tipo);
        String rutaFiltro = normalizarFiltro(ruta);
        String estadoFiltro = normalizarEstadoFiltro(estado);
        return incidenteRepository.findForPanel(tipoFiltro, rutaFiltro, estadoFiltro, pageable)
                .map(incidente -> new IncidentePanelResponse(
                        incidente.getId(),
                        incidente.getRuta() == null ? "General" : incidente.getRuta().getNombre(),
                        incidente.getTipo(),
                        incidente.getDescripcion(),
                        incidente.getFechaReporte(),
                        incidente.getEstado()));
    }

    @Transactional(readOnly = true)
    public List<IncidenteRutaResponse> listarActivosDeRuta(Long rutaId) {
        if (rutaId == null || rutaId <= 0) {
            throw new AulaException("El identificador de la ruta no es válido.", "VALIDATION_ERROR");
        }
        if (!rutaRepository.existsById(rutaId)) {
            throw new AulaException("La ruta seleccionada no existe.", "ROUTE_NOT_FOUND");
        }

        return incidenteRepository
                .findByRuta_IdAndEstadoInOrderByFechaReporteDescIdDesc(rutaId, ESTADOS_ACTIVOS)
                .stream()
                .map(incidente -> new IncidenteRutaResponse(
                        incidente.getTipo(),
                        incidente.getEstado(),
                        incidente.getFechaReporte()))
                .toList();
    }

    @Transactional
    public Incidente reportar(IncidenteReportRequest solicitud, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new AulaException("No se encontró el usuario autenticado.", "USER_NOT_FOUND");
        }

        Ruta ruta = rutaRepository.findById(solicitud.getRutaId())
                .orElseThrow(() -> new AulaException("La ruta seleccionada no existe.", "ROUTE_NOT_FOUND"));

        Incidente incidente = new Incidente();
        incidente.setUsuario(usuario);
        incidente.setRuta(ruta);
        incidente.setTipo(solicitud.getTipo().name());
        incidente.setTitulo(tituloPara(solicitud.getTipo()));
        incidente.setDescripcion(solicitud.getDescripcion().trim());
        Incidente guardado = incidenteRepository.saveAndFlush(incidente);
        avisoFavoritoService.notificarIncidenteNuevo(guardado);
        return guardado;
    }

    @Transactional
    public Incidente cambiarEstado(Long incidenteId, String estado) {
        if (incidenteId == null || incidenteId <= 0) {
            throw new AulaException("El identificador del incidente no es válido.", "VALIDATION_ERROR");
        }
        if (estado == null || estado.isBlank()) {
            throw new AulaException("Indica el estado del incidente.", "VALIDATION_ERROR");
        }

        String estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_PERMITIDOS.contains(estadoNormalizado)) {
            throw new AulaException("El estado indicado no es válido.", "VALIDATION_ERROR");
        }

        Incidente incidente = incidenteRepository.findById(incidenteId)
                .orElseThrow(() -> new AulaException(
                        "No se encontró el incidente solicitado.", "INCIDENT_NOT_FOUND"));
        boolean cambio = !estadoNormalizado.equals(incidente.getEstado());
        incidente.setEstado(estadoNormalizado);
        Incidente actualizado = incidenteRepository.saveAndFlush(incidente);
        if (cambio) {
            avisoFavoritoService.notificarCambioDeEstado(actualizado);
        }
        return actualizado;
    }

    private String normalizarFiltro(String filtro) {
        return filtro == null || filtro.isBlank() ? null : filtro.trim();
    }

    private String normalizarEstadoFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }

        String estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_PERMITIDOS.contains(estadoNormalizado)) {
            throw new AulaException("El estado indicado no es válido.", "VALIDATION_ERROR");
        }
        return estadoNormalizado;
    }

    private String tituloPara(IncidenteReportRequest.TipoIncidente tipo) {
        return switch (tipo) {
            case RETRASO -> "Retraso reportado";
            case ACCIDENTE -> "Accidente reportado";
            case FALLA_SERVICIO -> "Falla del bus o del servicio";
            case RUTA_DESVIADA -> "Desvío de ruta reportado";
            case OTRO -> "Incidente reportado";
        };
    }
}
