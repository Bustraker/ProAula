package com.proaula.aula.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Service.ParadaCoordenadasService;
import com.proaula.aula.dto.CoordenadasParadaRequest;

import jakarta.validation.Valid;

@Controller
public class CoordenadasParadaController {
    private final ParadaCoordenadasService paradaCoordenadasService;

    public CoordenadasParadaController(ParadaCoordenadasService paradaCoordenadasService) {
        this.paradaCoordenadasService = paradaCoordenadasService;
    }

    @GetMapping("/admin/coordenadas-paradas")
    public String mostrarCoordenadas(
            Model model,
            @RequestParam(required = false) String error) {
        model.addAttribute("paradas", paradaCoordenadasService.listarParadas());
        model.addAttribute("errorCoordenadas", error);
        return "Admin/coordenadas_paradas";
    }

    @PostMapping("/admin/paradas/{id}/coordenadas")
    public String guardarCoordenadas(
            @PathVariable Long id,
            @Valid @ModelAttribute CoordenadasParadaRequest coordenadas,
            BindingResult resultado,
            RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            return "redirect:/admin/coordenadas-paradas?error=datos";
        }

        Parada parada = paradaCoordenadasService.actualizar(id, coordenadas).orElse(null);
        if (parada == null) {
            return "redirect:/admin/coordenadas-paradas?error=parada";
        }
        redirectAttributes.addFlashAttribute("coordenadasGuardadas",
            "Coordenadas guardadas para " + parada.getNombre() + ".");
        return "redirect:/admin/coordenadas-paradas";
    }
}
