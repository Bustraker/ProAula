package com.proaula.aula.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Service.BarrioService;
import com.proaula.aula.Service.RutaService;

@RestController
@RequestMapping("/api")
public class RutaRestController {
    private static final String CAMPO_NOMBRE = "nombre";
    private static final String CAMPO_BARRIOS = "barrios";

    private final RutaService rutaService;
    private final BarrioService barrioService;

    public RutaRestController(RutaService rutaService, BarrioService barrioService) {
        this.rutaService = rutaService;
        this.barrioService = barrioService;
    }
    
    // API REST para obtener rutas con coordenadas
    @GetMapping("/rutas-con-coordenadas")
    public ResponseEntity<List<Map<String, Object>>> obtenerRutasConCoordenadas(Authentication authentication) {
        boolean autenticado = authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
        List<Map<String, Object>> rutasConCoords = new ArrayList<>();
        
        rutaService.getRutasVerificadas().forEach(ruta -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("id", ruta.getId());
            mapa.put(CAMPO_NOMBRE, ruta.getNombre());
            mapa.put(CAMPO_BARRIOS, ruta.getBarrios());
            mapa.put("barriosOrdenados", ruta.getBarriosOrdenados());
            mapa.put("ordenBarriosConfirmado", ruta.isOrdenBarriosConfirmado());
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
            List<String> barriosCoordenadas = ruta.isOrdenBarriosConfirmado()
                ? ruta.getBarriosOrdenados()
                : ruta.getBarrios();
            Map<String, double[]> coords = barrioService.obtenerCoordenadas(barriosCoordenadas);
            mapa.put("coordenadasBarrios", coords);

            List<Map<String, Object>> buses = new ArrayList<>();
            ruta.getBuses().forEach(bus -> {
                Map<String, Object> datosBus = new HashMap<>();
                datosBus.put("id", bus.getId());
                datosBus.put("placa", bus.getPlaca());
                datosBus.put("modelo", bus.getModelo());
                datosBus.put("color", bus.getColor());
                if (autenticado) {
                    datosBus.put("conductor", bus.getConductor());
                }
                buses.add(datosBus);
            });
            mapa.put("buses", buses);
            
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
            mapa.put(CAMPO_NOMBRE, ruta.getNombre());
            mapa.put(CAMPO_BARRIOS, ruta.getBarrios());
            mapa.put("barriosOrdenados", ruta.getBarriosOrdenados());
            mapa.put("ordenBarriosConfirmado", ruta.isOrdenBarriosConfirmado());
            mapa.put("horaAproximada", ruta.getHoraAproximada() != null ? ruta.getHoraAproximada().toString() : null);
            rutas.add(mapa);
        });
        
        return ResponseEntity.ok(rutas);
    }
}
