package com.proaula.aula.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Service.BarrioService;
import com.proaula.aula.Service.RutaService;

import java.util.Objects;

@Controller
public class RutaController {
    private static final String ERROR_BARRIOS = "barrios";

    private final RutaService rutaService;
    private final BarrioService barrioService;

    public RutaController(RutaService rutaService, BarrioService barrioService) {
        this.rutaService = rutaService;
        this.barrioService = barrioService;
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
            ruta != null && Boolean.TRUE.equals(ruta.getVerificada()) && ruta.isOrdenBarriosConfirmado()
                ? String.join(", ", ruta.getBarriosOrdenados())
                : "");
        model.addAttribute("ordenPendiente", ruta != null
            && (!Boolean.TRUE.equals(ruta.getVerificada()) || !ruta.isOrdenBarriosConfirmado()));
        model.addAttribute("errorBarrios", ERROR_BARRIOS.equals(error));
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/editar_ruta";
    }

    @PostMapping("/editar-ruta/{id}")
    public String guardarEdicionRuta(@PathVariable Long id, @ModelAttribute Ruta ruta,
                                     @RequestParam String barriosOrdenadosTexto,
                                     @RequestParam(value = "accesibleMovilidadReducida", required = false)
                                     Boolean accesibleMovilidadReducida) {
        Ruta rutaExistente = rutaService.getRutaById(id);
        if (rutaExistente == null) {
            return "redirect:/editar-ruta";
        }

        List<String> barriosOrdenados = procesarBarrios(barriosOrdenadosTexto);
        if (barriosOrdenados.stream().distinct().count() < 2) {
            return "redirect:/editar-ruta/" + id + "?error=barrios";
        }
        String origen = barriosOrdenados.get(0);
        String destino = barriosOrdenados.get(barriosOrdenados.size() - 1);
        boolean cambio = !Objects.equals(rutaExistente.getBarrios(), barriosOrdenados)
                || !Objects.equals(rutaExistente.getBarriosOrdenados(), barriosOrdenados)
                || !Objects.equals(rutaExistente.getOrigen(), origen)
                || !Objects.equals(rutaExistente.getDestino(), destino)
                || !Objects.equals(rutaExistente.getVerificada(), Boolean.TRUE.equals(ruta.getVerificada()))
                || !Objects.equals(rutaExistente.getAccesibleMovilidadReducida(), accesibleMovilidadReducida);
        rutaExistente.setBarrios(new ArrayList<>(barriosOrdenados));
        rutaExistente.setBarriosOrdenados(barriosOrdenados);
        rutaExistente.setOrigen(origen);
        rutaExistente.setDestino(destino);
        rutaExistente.setVerificada(Boolean.TRUE.equals(ruta.getVerificada()));
        rutaExistente.setAccesibleMovilidadReducida(accesibleMovilidadReducida);
        rutaService.saveRuta(rutaExistente, cambio);
        return "redirect:/editar-ruta";
    }

    // Vista pública para usuarios - Lista de rutas
    @GetMapping("/rutas")
    public String listarRutas(Model model,
                             @RequestParam(required = false) String buscar,
                             @RequestParam(required = false) String barrio,
                             @RequestParam(required = false) String accesibilidad) {
        if (accesibilidad != null && !accesibilidad.isBlank()
                && !List.of("accesible", "no-accesible", "sin-datos").contains(accesibilidad)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de accesibilidad no válido.");
        }
        String barrioFiltro = barrio == null || barrio.isBlank()
            ? null
            : barrioService.obtenerNombreCanonico(barrio);
        if (barrioFiltro != null && barrioFiltro.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barrio catastral no válido.");
        }
        List<Ruta> todasLasRutas = rutaService.getAllRutas().stream()
            .filter(ruta -> ruta.getEstado() == Ruta.Estado.ACTIVA)
            .collect(Collectors.toCollection(ArrayList::new));
        List<Ruta> rutas = new ArrayList<>(todasLasRutas);
        
        // Filtros
        if (buscar != null && !buscar.isEmpty()) {
            rutas = rutas.stream()
                .filter(r -> r.getNombre() != null && r.getNombre().toLowerCase().contains(buscar.toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (barrioFiltro != null) {
            rutas = rutas.stream()
                .filter(r -> r.getBarrios() != null && r.getBarrios().contains(barrioFiltro))
                .collect(Collectors.toList());
        }

        if (accesibilidad != null && !accesibilidad.isBlank()) {
            rutas = rutas.stream()
                .filter(ruta -> switch (accesibilidad) {
                    case "accesible" -> Boolean.TRUE.equals(ruta.getAccesibleMovilidadReducida());
                    case "no-accesible" -> Boolean.FALSE.equals(ruta.getAccesibleMovilidadReducida());
                    case "sin-datos" -> ruta.getAccesibleMovilidadReducida() == null;
                    default -> false;
                })
                .collect(Collectors.toList());
        }
        
        // Obtener barrios únicos
        List<String> barrios = todasLasRutas.stream()
            .flatMap(r -> r.getBarrios() != null ? r.getBarrios().stream() : new ArrayList<String>().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        model.addAttribute("rutas", rutas);
        model.addAttribute("hayRutasProvisionales",
            todasLasRutas.stream().anyMatch(r -> !Boolean.TRUE.equals(r.getVerificada())));
        model.addAttribute("barriosDisponibles", barrios);
        model.addAttribute("buscar", buscar);
        model.addAttribute("barrioSeleccionado", barrioFiltro);
        model.addAttribute("accesibilidadSeleccionada", accesibilidad);
        
        return "rutas-lista";
    }
    
    // Vista de detalle de ruta
    @GetMapping("/ruta/{id}")
    public String detalleRuta(@PathVariable Long id, Model model) {
        Ruta ruta = rutaService.getRutaById(id);
        if (ruta != null) {
            model.addAttribute("ruta", ruta);
            model.addAttribute("paradasIda", rutaService.getParadasPorSentido(id, RutaParada.Sentido.IDA));
            model.addAttribute("paradasVuelta", rutaService.getParadasPorSentido(id, RutaParada.Sentido.VUELTA));
            model.addAttribute("paradasRuta", rutaService.getParadasPorRuta(id));
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
        model.addAttribute("errorRelaciones", "relaciones".equals(error));
        return "Admin/agregar_rutas";
    }

    @PostMapping("/rutas")
    public String agregarRuta(@Valid @ModelAttribute Ruta ruta, @RequestParam String barriosOrdenadosTexto) {
        List<String> barriosOrdenados = procesarBarrios(barriosOrdenadosTexto);
        if (barriosOrdenados.stream().distinct().count() < 2) {
            return "redirect:/rutas/admin?error=barrios";
        }
        ruta.setBarrios(new ArrayList<>(barriosOrdenados));
        ruta.setBarriosOrdenados(barriosOrdenados);
        ruta.setOrigen(barriosOrdenados.get(0));
        ruta.setDestino(barriosOrdenados.get(barriosOrdenados.size() - 1));
        rutaService.saveRuta(ruta);
        return "redirect:/rutas";
    }

    @PostMapping("/eliminar-ruta/{id}")
    public String eliminarRuta(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (rutaService.hasRelatedRecords(id)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "No se puede eliminar la ruta porque tiene buses, paradas, incidentes o historial asociado.");
            return "redirect:/rutas/admin?error=relaciones";
        }
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
                String nombreCanonico = barrioService.obtenerNombreCanonico(barrioLimpio);
                if (nombreCanonico.isBlank()) {
                    return new ArrayList<>();
                }
                barriosProcesados.add(nombreCanonico);
            }
        }
        return barriosProcesados;
    }
}
