package com.proaula.aula.Controller;

import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.ContactoMensajeService;
import com.proaula.aula.Service.RutaService;
import com.proaula.aula.Service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final UsuarioService usuarioService;
    private final RutaService rutaService;
    private final BusService busService;
    private final ContactoMensajeService contactoMensajeService;

    public DashboardController(UsuarioService usuarioService,
                               RutaService rutaService,
                               BusService busService,
                               ContactoMensajeService contactoMensajeService) {
        this.usuarioService = usuarioService;
        this.rutaService = rutaService;
        this.busService = busService;
        this.contactoMensajeService = contactoMensajeService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        // Ruta protegida, solo verifica usuario
        String username = authentication.getName();
        Usuario usuario = usuarioService.findByUsername(username);
        
        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado?error=usuario_no_encontrado";
        }
        
        model.addAttribute("usuario", usuario);
        model.addAttribute("totalRutas", rutaService.count());
        model.addAttribute("rutasPopulares", rutaService.findTop4ByOrderByNombre());
        model.addAttribute("viajesRealizados", 0); // Se puede implementar después
        model.addAttribute("proximoViaje", "--");
        model.addAttribute("busesActivos", rutaService.countActiveBuses());
        model.addAttribute("buses", busService.getAllBuses()); // Para la sección de buses activos
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        
        return "Usuario/index_3";
    }
    
    /**
     * Panel principal admin
     */
    @GetMapping("/index_2")
    public String adminDashboard(Model model, Authentication authentication) {
        // Ruta admin protegida, confirma usuario
        String username = authentication.getName();
        Usuario usuario = usuarioService.findByUsername(username);
        
        if (usuario == null) {
            return "redirect:/admin-login?error=usuario_no_encontrado";
        }
        
        // Carga estadísticas
        long totalUsuarios = usuarioService.count();
        long totalBuses = busService.count();
        long totalRutas = rutaService.count();
        long totalMensajes = contactoMensajeService.count();
        
        // Añade datos al modelo
        model.addAttribute("usuario", usuario);
        model.addAttribute("totalUsuarios", totalUsuarios);
        model.addAttribute("totalBuses", totalBuses);
        model.addAttribute("totalRutas", totalRutas);
        model.addAttribute("totalMensajes", totalMensajes);
        
        // Carga actividad reciente
        model.addAttribute("usuariosRecientes", usuarioService.getAllUsuarios());
        model.addAttribute("rutasRecientes", rutaService.getAllRutas());
        model.addAttribute("busesRecientes", busService.getAllBuses());
        model.addAttribute("mensajesRecientes", contactoMensajeService.getAllMensajes());
        
        return "Admin/index2";
    }
}
