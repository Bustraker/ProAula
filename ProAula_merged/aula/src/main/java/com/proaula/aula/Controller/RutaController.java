package com.proaula.aula.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Service.RutaService;

@Controller
public class RutaController {
    @Autowired
    private RutaService rutaService;
    
    // Vista para editar rutas
    @GetMapping("/editar-ruta")
    public String mostrarEditarRuta(Model model) {
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/editar_ruta";
    }

    @GetMapping("/editar-ruta/{id}")
    public String editarRutaPorId(@PathVariable Long id, Model model) {
        Ruta ruta = rutaService.getRutaById(id);
        model.addAttribute("ruta", ruta);
        model.addAttribute("barrios", ruta != null ? ruta.getBarrios() : new ArrayList<>());
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/editar_ruta";
    }

    @PostMapping("/editar-ruta/{id}")
    public String guardarEdicionRuta(@PathVariable Long id, @ModelAttribute Ruta ruta) {
        ruta.setId(id);
        procesarBarrios(ruta);
        rutaService.saveRuta(ruta);
        return "redirect:/editar-ruta";
    }

    // Vista pública para usuarios - Lista de rutas
    @GetMapping("/rutas")
    public String listarRutas(Model model,
                             @RequestParam(required = false) String buscar,
                             @RequestParam(required = false) String barrio) {
        List<Ruta> rutas = rutaService.getAllRutas();
        
        // Filtros
        if (buscar != null && !buscar.isEmpty()) {
            rutas = rutas.stream()
                .filter(r -> r.getNombre().toLowerCase().contains(buscar.toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (barrio != null && !barrio.isEmpty()) {
            rutas = rutas.stream()
                .filter(r -> r.getBarrios() != null && r.getBarrios().contains(barrio))
                .collect(Collectors.toList());
        }
        
        // Obtener barrios únicos
        List<String> barrios = rutaService.getAllRutas().stream()
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
        if (ruta != null) {
            model.addAttribute("ruta", ruta);
            return "detalle-ruta";
        }
        return "redirect:/rutas";
    }

    // Páginas de gestión de rutas (apuntan a plantillas en Admin si aplica)
    @GetMapping("/rutas/admin")
    public String gestionarRutas(Model model) {
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
        return "Admin/agregar_rutas";
    }

    @PostMapping("/rutas")
    public String agregarRuta(@ModelAttribute Ruta ruta) {
        // Parsear barrios desde string separado por coma
        procesarBarrios(ruta);
        rutaService.saveRuta(ruta);
        return "redirect:/rutas";
    }

    @PostMapping("/eliminar-ruta/{id}")
    public String eliminarRuta(@PathVariable Long id) {
        rutaService.deleteRuta(id);
        return "redirect:/rutas";
    }

    /**
     * Método auxiliar para procesar los barrios
     * Maneja tanto cadenas separadas por coma como listas
     */
    private void procesarBarrios(Ruta ruta) {
        if (ruta.getBarrios() != null && !ruta.getBarrios().isEmpty()) {
            List<String> barriosProcesados = new ArrayList<>();
            for (String barrio : ruta.getBarrios()) {
                if (barrio != null && !barrio.trim().isEmpty()) {
                    // Si contiene comas, dividir; si no, agregar tal cual
                    if (barrio.contains(",")) {
                        String[] barrios = barrio.split(",");
                        for (String b : barrios) {
                            String barrioLimpio = b.trim();
                            if (!barrioLimpio.isEmpty()) {
                                barriosProcesados.add(barrioLimpio);
                            }
                        }
                    } else {
                        barriosProcesados.add(barrio.trim());
                    }
                }
            }
            ruta.setBarrios(barriosProcesados.isEmpty() ? null : barriosProcesados);
        }
    }
}
