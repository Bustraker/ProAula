package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Service.IncidenteService;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.dto.IncidenteReportResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/incidentes")
public class IncidenteRestController {

    private final IncidenteService incidenteService;

    public IncidenteRestController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
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
}
