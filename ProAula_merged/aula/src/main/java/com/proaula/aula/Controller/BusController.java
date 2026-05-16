package com.proaula.aula.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.RutaService;

@Controller
public class BusController {
    @Autowired
    private BusService busService;
    @Autowired
    private RutaService rutaService;

    // Vistas para administración (templates/Admin/*)
    @GetMapping("/registro-buses")
    public String registroBuses(Model model) {
        model.addAttribute("bus", new Bus());
        model.addAttribute("rutas", rutaService.getAllRutas());
        return "Admin/registro_de_buses";
    }

    @PostMapping("/registro-buses")
    public String registrarBus(@ModelAttribute Bus bus) {
        busService.saveBus(bus);
        return "redirect:/index_2";
    }

    @GetMapping("/actualizarbuses")
    public String actualizarBuses(Model model) {
        model.addAttribute("buses", busService.getAllBuses());
        return "Admin/actualizarbuses";
    }

    @PostMapping("/actualizar-bus/{id}")
    public String actualizarBus(@PathVariable Long id, @ModelAttribute Bus bus) {
        bus.setId(id);
        busService.saveBus(bus);
        return "redirect:/index_2";
    }

    @GetMapping("/eliminarbuses")
    public String eliminarBuses(Model model) {
        model.addAttribute("buses", busService.getAllBuses());
        return "Admin/eliminarbuses";
    }

    @PostMapping("/eliminar-buses")
    public String eliminarBus(@RequestParam Long busId) {
        busService.deleteBus(busId);
        return "redirect:/index_2";
    }
}