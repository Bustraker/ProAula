package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Service.IncidenteService;
import com.proaula.aula.dto.IncidenteEstadoRequest;
import com.proaula.aula.dto.IncidentePageResponse;
import com.proaula.aula.dto.IncidentePanelResponse;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.dto.IncidenteReportResponse;
import com.proaula.aula.exception.AulaException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
@RestController
@RequestMapping("/api/incidentes")
public class IncidenteRestController {

    private static final int MAX_TAMANO_PAGINA = 100;

    private final IncidenteService incidenteService;

    public IncidenteRestController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
    }

    @GetMapping
    public IncidentePageResponse listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String ruta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_TAMANO_PAGINA) {
            throw new AulaException(
                    "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y "
                            + MAX_TAMANO_PAGINA + ".",
                    "VALIDATION_ERROR");
        }

        Page<IncidentePanelResponse> resultado =
                incidenteService.listar(tipo, ruta, PageRequest.of(page, size));
        return new IncidentePageResponse(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    @PostMapping
    public ResponseEntity<IncidenteReportResponse> reportar(
            @Valid @RequestBody IncidenteReportRequest solicitud,
            Authentication authentication) {
        Incidente incidente = incidenteService.reportar(solicitud, authentication.getName());
        IncidenteReportResponse respuesta = new IncidenteReportResponse(
                incidente.getId(),
                incidente.getTipo(),
                incidente.getRuta().getId(),
                incidente.getDescripcion(),
                incidente.getEstado(),
                incidente.getFechaReporte());
        return ResponseEntity.created(URI.create("/api/incidentes/" + incidente.getId())).body(respuesta);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<IncidenteReportResponse> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody IncidenteEstadoRequest solicitud) {
        Incidente incidente = incidenteService.cambiarEstado(id, solicitud.getEstado());
        IncidenteReportResponse respuesta = new IncidenteReportResponse(
                incidente.getId(),
                incidente.getTipo(),
                incidente.getRuta() == null ? null : incidente.getRuta().getId(),
                incidente.getDescripcion(),
                incidente.getEstado(),
                incidente.getFechaReporte());
        return ResponseEntity.ok(respuesta);
    }
}
