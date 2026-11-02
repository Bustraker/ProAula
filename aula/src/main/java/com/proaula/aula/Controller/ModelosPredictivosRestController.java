package com.proaula.aula.Controller;

import com.proaula.aula.Service.ModelosPredictivosService;
import com.proaula.aula.dto.modelos.RecomendacionRutaRequest;
import com.proaula.aula.dto.modelos.RecomendacionRutaResponse;
import com.proaula.aula.dto.modelos.RiesgoRetrasoRequest;
import com.proaula.aula.dto.modelos.RiesgoRetrasoResponse;
import com.proaula.aula.dto.modelos.SugerenciasDestinoRequest;
import com.proaula.aula.dto.modelos.SugerenciasDestinoResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modelos")
public class ModelosPredictivosRestController {
    private final ModelosPredictivosService modelosService;

    public ModelosPredictivosRestController(ModelosPredictivosService modelosService) {
        this.modelosService = modelosService;
    }

    @PostMapping("/retrasos/riesgo")
    public ResponseEntity<RiesgoRetrasoResponse> predecirRiesgo(
            @Valid @RequestBody RiesgoRetrasoRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(modelosService.predecirRiesgo(request));
    }

    @PostMapping("/rutas/recomendar")
    public ResponseEntity<RecomendacionRutaResponse> recomendarRuta(
            @Valid @RequestBody RecomendacionRutaRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(modelosService.recomendarRuta(request));
    }

    @PostMapping("/destinos/sugerir")
    public ResponseEntity<SugerenciasDestinoResponse> sugerirDestinos(
            @Valid @RequestBody SugerenciasDestinoRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(modelosService.sugerirDestinos(request));
    }
}
