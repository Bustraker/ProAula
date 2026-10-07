package com.proaula.aula.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.RutaService;

@Controller
public class RutaController {
    private static final String ERROR_BARRIOS = "barrios";

    private final RutaService rutaService;

    public RutaController(RutaService rutaService) {
        this.rutaService = rutaService;
    }
    
    // Vista para editar rutas
    @GetMapping("/editar-ruta")
    public String mostrarEditarRuta(Model model) {
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/editar_ruta";
    }

    @GetMapping("/editar-ruta/{id}")
    public String editarRutaPorId(@PathVariable Long id, Model model,
                                  @RequestParam(required = false) String error) {
        Ruta ruta = rutaService.getRutaById(id);
        model.addAttribute("ruta", ruta);
        model.addAttribute("barrios", ruta != null ? ruta.getBarrios() : new ArrayList<>());
        model.addAttribute("barriosOrdenadosTexto",
            ruta != null && ruta.isOrdenBarriosConfirmado()
                ? String.join(", ", ruta.getBarriosOrdenados())
                : "");
        model.addAttribute("ordenPendiente", ruta != null && !ruta.isOrdenBarriosConfirmado());
        model.addAttribute("errorBarrios", ERROR_BARRIOS.equals(error));
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/editar_ruta";
    }

    @PostMapping("/editar-ruta/{id}")
    public String guardarEdicionRuta(@PathVariable Long id, @ModelAttribute Ruta ruta,
                                     @RequestParam String barriosOrdenadosTexto) {
        Ruta rutaExistente = rutaService.getRutaById(id);
        if (rutaExistente == null) {
            return "redirect:/editar-ruta";
        }

        List<String> barriosOrdenados = procesarBarrios(barriosOrdenadosTexto);
        if (barriosOrdenados.stream().distinct().count() < 2) {
            return "redirect:/editar-ruta/" + id + "?error=barrios";
        }
        rutaExistente.setBarrios(new ArrayList<>(barriosOrdenados));
        rutaExistente.setBarriosOrdenados(barriosOrdenados);
        rutaExistente.setVerificada(Boolean.TRUE.equals(ruta.getVerificada()));
        rutaService.saveRuta(rutaExistente);
        return "redirect:/editar-ruta";
    }

    // Vista pública para usuarios - Lista de rutas
    @GetMapping("/rutas")
    public String listarRutas(Model model,
                             @RequestParam(required = false) String buscar,
                             @RequestParam(required = false) String barrio) {
        List<Ruta> rutas = rutaService.getRutasVerificadas();
        
        // Filtros
        if (buscar != null && !buscar.isEmpty()) {
            rutas = rutas.stream()
                .filter(r -> r.getNombre() != null && r.getNombre().toLowerCase().contains(buscar.toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (barrio != null && !barrio.isEmpty()) {
            rutas = rutas.stream()
                .filter(r -> r.getBarrios() != null && r.getBarrios().contains(barrio))
                .collect(Collectors.toList());
        }
        
        // Obtener barrios únicos
        List<String> barrios = rutaService.getRutasVerificadas().stream()
            .flatMap(r -> r.getBarrios() != null ? r.getBarrios().stream() : new ArrayList<String>().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        model.addAttribute("rutas", rutas);
        model.addAttribute("barriosDisponibles", barrios);
        model.addAttribute("buscar", buscar);
        model.addAttribute("barrioSeleccionado", barrio);
        
        return "rutas-lista";
    }
    
    // Vista de detalle de ruta
    @GetMapping("/ruta/{id}")
    public String detalleRuta(@PathVariable Long id, Model model) {
        Ruta ruta = rutaService.getRutaById(id);
        if (ruta != null && Boolean.TRUE.equals(ruta.getVerificada())) {
            model.addAttribute("ruta", ruta);
            return "detalle-ruta";
        }
        return "redirect:/rutas";
    }

    // Gestión de rutas para admin
    @GetMapping("/rutas/admin")
    public String gestionarRutas(Model model, @RequestParam(required = false) String error) {
        List<Ruta> rutas = rutaService.getAllRutas();
        // Formatear la hora para cada ruta
        List<String> horasFormateadas = new ArrayList<>();
        for (Ruta ruta : rutas) {
            if (ruta.getHoraAproximada() != null) {
                horasFormateadas.add(ruta.getHoraAproximada().toString().substring(0,5));
            } else {
                horasFormateadas.add("");
            }
        }
        model.addAttribute("rutas", rutas);
        model.addAttribute("horasFormateadas", horasFormateadas);
        model.addAttribute("newRuta", new Ruta());
        model.addAttribute("errorBarrios", ERROR_BARRIOS.equals(error));
        return "Admin/agregar_rutas";
    }

    @PostMapping("/rutas")
    public String agregarRuta(@ModelAttribute Ruta ruta, @RequestParam String barriosOrdenadosTexto) {
        List<String> barriosOrdenados = procesarBarrios(barriosOrdenadosTexto);
        if (barriosOrdenados.stream().distinct().count() < 2) {
            return "redirect:/rutas/admin?error=barrios";
        }
        ruta.setBarrios(new ArrayList<>(barriosOrdenados));
        ruta.setBarriosOrdenados(barriosOrdenados);
        rutaService.saveRuta(ruta);
        return "redirect:/rutas";
    }

    @PostMapping("/eliminar-ruta/{id}")
    public String eliminarRuta(@PathVariable Long id) {
        rutaService.deleteRuta(id);
        return "redirect:/rutas";
    }

    // Procesa barrios de ruta desde texto o lista.
    private List<String> procesarBarrios(String texto) {
        if (texto == null || texto.isBlank()) {
            return new ArrayList<>();
        }
        List<String> barriosProcesados = new ArrayList<>();
        for (String barrio : texto.split(",")) {
            String barrioLimpio = barrio.trim();
            if (!barrioLimpio.isEmpty()) {
                barriosProcesados.add(barrioLimpio);
            }
        }
        return barriosProcesados;
    }
}
