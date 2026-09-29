package com.proaula.aula.Controller;

import com.proaula.aula.Service.BarrioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/barrios")
@CrossOrigin(origins = "*")
public class BarrioController {
    
    @Autowired
    private BarrioService barrioService;
    
    /**
     * Obtiene todos los barrios con coordenadas
     * GET /api/barrios/todos
     */
    @GetMapping("/todos")
    public ResponseEntity<List<Map<String, Object>>> obtenerTodos() {
        List<Map<String, Object>> barrios = new ArrayList<>();
        
        barrioService.obtenerTodos().forEach(barrio -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("id", barrio.getId());
            mapa.put("nombre", barrio.getNombre());
            mapa.put("latitud", barrio.getLatitud());
            mapa.put("longitud", barrio.getLongitud());
            mapa.put("localidad", barrio.getLocalidad());
            barrios.add(mapa);
        });
        
        return ResponseEntity.ok(barrios);
    }
    
    /**
     * Obtiene coordenadas para una lista de barrios
     * POST /api/barrios/coordenadas
     * Body: ["centro", "la matuna", "nelson mandela"]
     */
    @PostMapping("/coordenadas")
    public ResponseEntity<Map<String, double[]>> obtenerCoordenadas(@RequestBody List<String> nombresBarrios) {
        Map<String, double[]> coordenadas = barrioService.obtenerCoordenadas(nombresBarrios);
        return ResponseEntity.ok(coordenadas);
    }
    
    /**
     * Obtiene un barrio por nombre
     * GET /api/barrios/nombre/{nombre}
     */
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<Map<String, Object>> obtenerPorNombre(@PathVariable String nombre) {
        return barrioService.obtenerPorNombre(nombre)
            .map(barrio -> {
                Map<String, Object> mapa = new HashMap<>();
                mapa.put("id", barrio.getId());
                mapa.put("nombre", barrio.getNombre());
                mapa.put("latitud", barrio.getLatitud());
                mapa.put("longitud", barrio.getLongitud());
                mapa.put("localidad", barrio.getLocalidad());
                return ResponseEntity.ok(mapa);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
