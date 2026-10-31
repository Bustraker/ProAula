package com.proaula.aula.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Service.LlegadaBusService;
import com.proaula.aula.dto.LlegadasRutaResponse;
import com.proaula.aula.dto.UbicacionBusReporteRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/llegadas")
public class LlegadaBusRestController {
    private final LlegadaBusService llegadaBusService;

    public LlegadaBusRestController(LlegadaBusService llegadaBusService) {
        this.llegadaBusService = llegadaBusService;
    }

    @GetMapping("/{rutaId}")
    public ResponseEntity<LlegadasRutaResponse> obtenerLlegadas(
            @PathVariable Long rutaId,
            @RequestParam(defaultValue = "IDA") RutaParada.Sentido sentido) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(llegadaBusService.obtenerLlegadas(rutaId, sentido));
    }

    @PostMapping("/{rutaId}/reportes")
    public ResponseEntity<Void> reportarUbicacion(
            @PathVariable Long rutaId,
            @RequestParam(defaultValue = "IDA") RutaParada.Sentido sentido,
            @Valid @RequestBody UbicacionBusReporteRequest ubicacion,
            Authentication authentication) {
        llegadaBusService.reportarUbicacion(rutaId, sentido,
            authentication == null ? null : authentication.getName(), ubicacion);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/reportes/actual")
    public ResponseEntity<Void> detenerReportes(Authentication authentication) {
        llegadaBusService.detenerReportes(authentication == null ? null : authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
