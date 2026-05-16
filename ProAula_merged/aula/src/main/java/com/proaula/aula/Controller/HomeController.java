package com.proaula.aula.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.AdminCodeService;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.ContactoMensajeService;
import com.proaula.aula.Service.RutaService;
import com.proaula.aula.Service.UsuarioService;

@Controller
public class HomeController {
    @Autowired
    private BusService busService;
    @Autowired
    private RutaService rutaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private AdminCodeService adminCodeService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ContactoMensajeService contactoMensajeService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping({"/inicio-de-sesion-mejorado.html", "/inicio-de-sesion-mejorado", "/inicio_de_sesion"})
    public String login(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "inicio-de-sesion-mejorado";
    }

    @GetMapping({"/registro-mejorado.html", "/registro-mejorado", "/registro"})
    public String registro(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "registro-mejorado";
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute Usuario usuario,
                           @RequestParam(required = false) String adminCode,
                           @RequestParam(required = false) boolean terminos,
                           Model model) {
        if (!terminos) {
            model.addAttribute("error", "Debes aceptar los términos y condiciones para registrarte");
            model.addAttribute("usuario", usuario);
            return "registro-mejorado";
        }

        if (usuarioService.findByUsername(usuario.getUsername()) != null) {
            model.addAttribute("error", "El nombre de usuario ya está en uso");
            model.addAttribute("usuario", usuario);
            return "registro-mejorado";
        }

        if (usuarioRepository.findByEmail(usuario.getEmail()) != null) {
            model.addAttribute("error", "El email ya está registrado");
            model.addAttribute("usuario", usuario);
            return "registro-mejorado";
        }

        String roleIngresado = usuario.getRole() != null ? usuario.getRole().trim().toUpperCase() : "";

        if ("ADMIN".equals(roleIngresado)) {
            if (adminCode == null || adminCode.trim().isEmpty()) {
                model.addAttribute("error", "Debes proporcionar el código de administrador para crear una cuenta de administrador");
                model.addAttribute("usuario", usuario);
                return "registro-mejorado";
            }
            if (!adminCodeService.isValidAdminCode(adminCode.trim())) {
                model.addAttribute("error", "Código de administrador incorrecto");
                model.addAttribute("usuario", usuario);
                return "registro-mejorado";
            }
            usuario.setRole("ROLE_ADMIN");
        } else {
            usuario.setRole("ROLE_USER");
        }

        usuarioService.register(usuario);

        if ("ROLE_ADMIN".equals(usuario.getRole())) {
            return "redirect:/admin-login?registrado=true";
        } else {
            return "redirect:/inicio-de-sesion-mejorado?registrado=true";
        }
    }

    @GetMapping("/index_3")
    public String index3(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "Usuario/index_3";
    }

    @GetMapping({"/public_index_3", "/index_3_public"})
    public String publicIndex3(Model model) {
        model.addAttribute("usuario", null);
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        return "Usuario/index_3_public";
    }

    @GetMapping("/viajar")
    public String viajar(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "Usuario/viajar";
    }

    @GetMapping("/contacto_usuario")
    public String contactoUsuario(Model model) {
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        model.addAttribute("usuario", new Usuario());
        return "Usuario/contacto";
    }

    @PostMapping("/contacto_usuario")
    public String contactoUsuarioSubmit(@ModelAttribute ContactoMensaje contactoMensaje, Model model) {
        contactoMensajeService.guardarMensaje(contactoMensaje);
        model.addAttribute("mensaje", "Gracias por tu mensaje. Nos pondremos en contacto contigo pronto.");
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        model.addAttribute("usuario", new Usuario());
        return "Usuario/contacto";
    }

    @GetMapping("/viajar_public")
    public String viajarPublic(Model model) {
        return "Usuario/viajar_public";
    }

    @GetMapping("/contacto_public")
    public String contactoPublic(Model model) {
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        return "Usuario/index_3_public";
    }

    @PostMapping("/contacto_public")
    public String contactoPublicPost(@ModelAttribute ContactoMensaje contactoMensaje, Model model) {
        contactoMensajeService.guardarMensaje(contactoMensaje);
        model.addAttribute("mensaje", "Gracias por tu consulta. Nos pondremos en contacto pronto.");
        model.addAttribute("contactoMensaje", new ContactoMensaje());
        return "Usuario/index_3_public";
    }

    @GetMapping("/gestionar-usuarios")
    public String gestionarUsuarios(@RequestParam(value = "buscar", defaultValue = "") String buscar, Model model) {
        List<Usuario> todos = usuarioService.getAllUsuarios();

        if (buscar != null && !buscar.isEmpty()) {
            String buscarLower = buscar.toLowerCase();
            todos = todos.stream()
                    .filter(u -> (u.getNombres() != null && u.getNombres().toLowerCase().contains(buscarLower))
                            || (u.getApellidos() != null && u.getApellidos().toLowerCase().contains(buscarLower))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(buscarLower))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(buscarLower)))
                    .collect(Collectors.toList());
        }

        // FIX: comparar correctamente con "ROLE_ADMIN" (antes comparaba con "ADMIN" sin prefijo)
        List<Usuario> administradores = todos.stream()
                .filter(u -> u.getRole() != null && u.getRole().equalsIgnoreCase("ROLE_ADMIN"))
                .sorted(Comparator.comparing(Usuario::getNombres, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Usuario::getApellidos, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        List<Usuario> usuarios = todos.stream()
                .filter(u -> u.getRole() == null || !u.getRole().equalsIgnoreCase("ROLE_ADMIN"))
                .sorted(Comparator.comparing(Usuario::getNombres, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Usuario::getApellidos, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        model.addAttribute("administradores", administradores);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("buscar", buscar);
        model.addAttribute("mensaje", "Gestión de usuarios");
        return "Admin/gestionar_usuarios";
    }

    @GetMapping("/editar-usuario/{id}")
    public String editarUsuario(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioService.getUsuarioById(id);
        if (usuario == null) {
            return "redirect:/gestionar-usuarios";
        }
        model.addAttribute("usuario", usuario);
        return "Admin/editar_usuario";
    }

    @PostMapping("/eliminar-usuario/{id}")
    public String eliminarUsuario(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        Usuario u = usuarioService.getUsuarioById(id);
        if (u != null) {
            usuarioService.deleteById(id);
            redirectAttrs.addFlashAttribute("mensaje", "Usuario eliminado correctamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensaje", "Usuario no encontrado.");
        }
        return "redirect:/gestionar-usuarios";
    }

    @PostMapping("/actualizar-usuario")
    public String actualizarUsuario(@ModelAttribute Usuario usuario,
                                    @RequestParam(required = false) String newPassword) {
        Usuario existente = usuarioService.getUsuarioById(usuario.getId());
        if (existente == null) {
            return "redirect:/gestionar-usuarios";
        }

        // FIX: actualizar solo campos de perfil con updateUsuario() para no re-encriptar contraseña
        Usuario datosActualizados = new Usuario();
        datosActualizados.setNombres(usuario.getNombres());
        datosActualizados.setApellidos(usuario.getApellidos());
        datosActualizados.setEmail(usuario.getEmail());
        usuarioService.updateUsuario(existente.getId(), datosActualizados);

        // Cambiar contraseña solo si se proporcionó una nueva no vacía
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            usuarioService.changePassword(existente.getId(), newPassword);
        }

        return "redirect:/gestionar-usuarios";
    }

    @PostMapping("/admin-crear-usuario")
    public String crearUsuario(@ModelAttribute Usuario usuario, Model model) {
        if (usuarioService.findByUsername(usuario.getUsername()) != null) {
            model.addAttribute("error", "El nombre de usuario ya está en uso");
            model.addAttribute("usuario", usuario);
            return "Admin/gestionar_usuarios";
        }

        if (usuarioRepository.findByEmail(usuario.getEmail()) != null) {
            model.addAttribute("error", "El email ya está registrado");
            model.addAttribute("usuario", usuario);
            return "Admin/gestionar_usuarios";
        }

        usuario.setRole("ROLE_ADMIN");
        usuarioService.register(usuario);
        return "redirect:/gestionar-usuarios?creado=true";
    }

    @GetMapping("/reportes")
    public String reportes(@RequestParam(value = "buscar", defaultValue = "") String buscar, Model model) {
        List<com.proaula.aula.Entity.Bus> buses = busService.getAllBuses();

        if (buscar != null && !buscar.isEmpty()) {
            String buscarLower = buscar.toLowerCase();
            buses = buses.stream()
                    .filter(b -> (b.getPlaca() != null && b.getPlaca().toLowerCase().contains(buscarLower))
                            || (b.getModelo() != null && b.getModelo().toLowerCase().contains(buscarLower)))
                    .collect(Collectors.toList());
        }

        model.addAttribute("totalBuses", busService.getAllBuses().size());
        model.addAttribute("buses", buses);
        model.addAttribute("totalRutas", rutaService.getAllRutas().size());
        model.addAttribute("totalUsuarios", usuarioService.getAllUsuarios().size());
        model.addAttribute("usuariosActivos", usuarioService.getAllUsuarios().size());
        model.addAttribute("buscar", buscar);
        return "Admin/reportes";
    }

    @GetMapping("/consultas")
    public String consultas(Model model) {
        model.addAttribute("buses", busService.getAllBuses());
        return "Usuario/consultas";
    }

    @GetMapping("/historial")
    public String historial(Model model, org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        Usuario usuario = usuarioService.findByUsername(username);
        model.addAttribute("usuario", usuario);
        return "Usuario/historial";
    }

    @GetMapping("/mensajes_contacto")
    public String mensajesContacto(Model model) {
        List<ContactoMensaje> mensajes = contactoMensajeService.getAllMensajes();
        model.addAttribute("mensajes", mensajes);
        return "Admin/lista_mensajes";
    }

    @PostMapping("/eliminar_mensaje/{id}")
    public String eliminarMensaje(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        contactoMensajeService.deleteMensaje(id);
        redirectAttrs.addFlashAttribute("mensaje", "Mensaje eliminado correctamente.");
        return "redirect:/mensajes_contacto";
    }

    @GetMapping("/privacidad")
    public String privacidad() { return "privacidad"; }

    @GetMapping("/privacidad.html")
    public String privacidadHtml() { return "privacidad"; }

    @GetMapping("/terminos")
    public String terminos() { return "terminos"; }

    @GetMapping("/terminos.html")
    public String terminosHtml() { return "terminos"; }
}
