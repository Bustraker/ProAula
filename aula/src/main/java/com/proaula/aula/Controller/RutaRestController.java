package com.proaula.aula.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Service.BarrioService;
import com.proaula.aula.Service.RutaService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RutaRestController {
    @Autowired
    private RutaService rutaService;
    @Autowired
    private BarrioService barrioService;
    
    // API REST para obtener rutas con coordenadas
    @GetMapping("/rutas-con-coordenadas")
    public ResponseEntity<List<Map<String, Object>>> obtenerRutasConCoordenadas() {
        List<Map<String, Object>> rutasConCoords = new ArrayList<>();
        
        rutaService.getRutasVerificadas().forEach(ruta -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("id", ruta.getId());
            mapa.put("nombre", ruta.getNombre());
            mapa.put("barrios", ruta.getBarrios());
            mapa.put("horaAproximada", ruta.getHoraAproximada() != null ? ruta.getHoraAproximada().toString() : null);
            List<Map<String, Object>> paradas = new ArrayList<>();
            ruta.getParadas().stream()
                .sorted((primera, segunda) -> Integer.compare(
                    primera.getOrden() == null ? Integer.MAX_VALUE : primera.getOrden(),
                    segunda.getOrden() == null ? Integer.MAX_VALUE : segunda.getOrden()))
                .forEach(parada -> {
                    Map<String, Object> datosParada = new HashMap<>();
                    datosParada.put("nombre", parada.getNombre());
                    datosParada.put("ubicacion", parada.getUbicacion());
                    datosParada.put("referencia", parada.getReferencia());
                    datosParada.put("barrio", parada.getBarrio());
                    datosParada.put("orden", parada.getOrden());
                    datosParada.put("latitud", parada.getLatitud());
                    datosParada.put("longitud", parada.getLongitud());
                    paradas.add(datosParada);
                });
            mapa.put("paradas", paradas);
            
            // Obtener coordenadas reales para cada barrio
            Map<String, double[]> coords = barrioService.obtenerCoordenadas(ruta.getBarrios());
            mapa.put("coordenadasBarrios", coords);
            
            rutasConCoords.add(mapa);
        });
        
        return ResponseEntity.ok(rutasConCoords);
    }
    
    // API REST para obtener todas las rutas
    @GetMapping("/rutas")
    public ResponseEntity<List<Map<String, Object>>> getAllRutas() {
        List<Map<String, Object>> rutas = new ArrayList<>();
        
        rutaService.getRutasVerificadas().forEach(ruta -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("id", ruta.getId());
            mapa.put("nombre", ruta.getNombre());
            mapa.put("barrios", ruta.getBarrios());
            mapa.put("horaAproximada", ruta.getHoraAproximada() != null ? ruta.getHoraAproximada().toString() : null);
            rutas.add(mapa);
        });
        
        return ResponseEntity.ok(rutas);
    }
}
