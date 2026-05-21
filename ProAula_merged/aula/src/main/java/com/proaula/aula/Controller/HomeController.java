package com.proaula.aula.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Comparator;

import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Entity.Viaje;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.ContactoMensajeDto;
import com.proaula.aula.dto.UsuarioFormDto;
import com.proaula.aula.Service.AdminCodeService;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.Service.ContactoMensajeService;
import com.proaula.aula.Service.RutaService;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.Service.ViajeService;

@Controller
public class HomeController {
    private static final String MODEL_ATTR_USUARIO = "usuario";
    private static final String MODEL_ATTR_ERROR = "error";
    private static final String VIEW_REGISTRO_MEJORADO = "registro-mejorado";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String MODEL_ATTR_CONTACTO_MENSAJE = "contactoMensaje";
    private static final String MODEL_ATTR_MENSAJE = "mensaje";
    private static final String MODEL_ATTR_BUSES = "buses";
    private static final String VIEW_ADMIN_GESTIONAR_USUARIOS = "Admin/gestionar_usuarios";
    private static final String VIEW_USUARIO_INDEX_3 = "Usuario/index_3";
    private static final String VIEW_USUARIO_VIAJAR = "Usuario/viajar";
    private static final String VIEW_USUARIO_CONTACTO = "Usuario/contacto";
    private static final String VIEW_USUARIO_HISTORIAL = "Usuario/historial";
    private static final String VIEW_USUARIO_INDEX_3_PUBLIC = "Usuario/index_3_public";
    private static final String REDIRECT_GESTIONAR_USUARIOS = "redirect:/gestionar-usuarios";

    private final BusService busService;
    private final RutaService rutaService;
    private final UsuarioService usuarioService;
    private final ViajeService viajeService;
    private final AdminCodeService adminCodeService;
    private final UsuarioRepository usuarioRepository;
    private final ContactoMensajeService contactoMensajeService;

    public HomeController(BusService busService,
                          RutaService rutaService,
                          UsuarioService usuarioService,
                          ViajeService viajeService,
                          AdminCodeService adminCodeService,
                          UsuarioRepository usuarioRepository,
                          ContactoMensajeService contactoMensajeService) {
        this.busService = busService;
        this.rutaService = rutaService;
        this.usuarioService = usuarioService;
        this.viajeService = viajeService;
        this.adminCodeService = adminCodeService;
        this.usuarioRepository = usuarioRepository;
        this.contactoMensajeService = contactoMensajeService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping({"/inicio-de-sesion-mejorado.html", "/inicio-de-sesion-mejorado", "/inicio_de_sesion"})
    public String login(Model model) {
        model.addAttribute(MODEL_ATTR_USUARIO, new UsuarioFormDto());
        return "inicio-de-sesion-mejorado";
    }

    @GetMapping({"/registro-mejorado.html", "/registro-mejorado", "/registro"})
    public String registro(Model model) {
        model.addAttribute(MODEL_ATTR_USUARIO, new UsuarioFormDto());
        return VIEW_REGISTRO_MEJORADO;
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute UsuarioFormDto usuarioForm,
                           @RequestParam(required = false) String adminCode,
                           @RequestParam(required = false) Boolean terminos,
                           Model model) {
        if (Boolean.FALSE.equals(terminos)) {
            model.addAttribute(MODEL_ATTR_ERROR, "Debes aceptar los términos y condiciones para registrarte");
            model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
            return VIEW_REGISTRO_MEJORADO;
        }

        if (usuarioService.findByUsername(usuarioForm.getUsername()) != null) {
            model.addAttribute(MODEL_ATTR_ERROR, "El nombre de usuario ya está en uso");
            model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
            return VIEW_REGISTRO_MEJORADO;
        }

        if (usuarioRepository.findByEmail(usuarioForm.getEmail()) != null) {
            model.addAttribute(MODEL_ATTR_ERROR, "El email ya está registrado");
            model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
            return VIEW_REGISTRO_MEJORADO;
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(usuarioForm.getUsername());
        usuario.setPassword(usuarioForm.getPassword());
        usuario.setNombres(usuarioForm.getNombres());
        usuario.setApellidos(usuarioForm.getApellidos());
        usuario.setEmail(usuarioForm.getEmail());
        usuario.setProvider(usuarioForm.getProvider());
        usuario.setProviderId(usuarioForm.getProviderId());

        String roleIngresado = usuarioForm.getRole() != null ? usuarioForm.getRole().trim().toUpperCase() : "";

        if ("ADMIN".equals(roleIngresado)) {
            if (adminCode == null || adminCode.trim().isEmpty()) {
                model.addAttribute(MODEL_ATTR_ERROR, "Debes proporcionar el código de administrador para crear una cuenta de administrador");
                model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
                return VIEW_REGISTRO_MEJORADO;
            }
            if (!adminCodeService.isValidAdminCode(adminCode.trim())) {
                model.addAttribute(MODEL_ATTR_ERROR, "Código de administrador incorrecto");
                model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
                return VIEW_REGISTRO_MEJORADO;
            }
            usuario.setRole(ROLE_ADMIN);
        } else {
            usuario.setRole(ROLE_USER);
        }

        usuarioService.register(usuario);

        if (ROLE_ADMIN.equals(usuario.getRole())) {
            return "redirect:/admin-login?registrado=true";
        } else {
            return "redirect:/inicio-de-sesion-mejorado?registrado=true";
        }
    }

    @GetMapping("/index_3")
    public String index3(Model model, Authentication authentication) {
        String username = authentication.getName();
        Usuario usuario = usuarioService.findByUsername(username);

        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado?error=usuario_no_encontrado";
        }

        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        model.addAttribute("totalRutas", rutaService.count());
        model.addAttribute("rutasPopulares", rutaService.findTop4ByOrderByNombre());
        model.addAttribute("viajesRealizados", 0);
        model.addAttribute("proximoViaje", "--");
        model.addAttribute("busesActivos", rutaService.countActiveBuses());
        model.addAttribute(MODEL_ATTR_BUSES, busService.getAllBuses());
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensaje());

        return VIEW_USUARIO_INDEX_3;
    }

    @GetMapping({"/public_index_3", "/index_3_public"})
    public String publicIndex3(Model model) {
        model.addAttribute(MODEL_ATTR_USUARIO, null);
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensaje());
        return VIEW_USUARIO_INDEX_3_PUBLIC;
    }

    @GetMapping("/viajar")
    public String viajar(Model model) {
        model.addAttribute(MODEL_ATTR_USUARIO, new UsuarioFormDto());
        return VIEW_USUARIO_VIAJAR;
    }

    @GetMapping("/contacto_usuario")
    public String contactoUsuario(Model model) {
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensajeDto());
        model.addAttribute(MODEL_ATTR_USUARIO, new UsuarioFormDto());
        return VIEW_USUARIO_CONTACTO;
    }

    @PostMapping("/contacto_usuario")
    public String contactoUsuarioSubmit(@ModelAttribute ContactoMensajeDto contactoMensajeDto, Model model) {
        ContactoMensaje contactoMensaje = new ContactoMensaje();
        contactoMensaje.setNombre(contactoMensajeDto.getNombre());
        contactoMensaje.setApellido(contactoMensajeDto.getApellido());
        contactoMensaje.setTelefono(contactoMensajeDto.getTelefono());
        contactoMensaje.setEmail(contactoMensajeDto.getEmail());
        contactoMensaje.setMensaje(contactoMensajeDto.getMensaje());

        contactoMensajeService.guardarMensaje(contactoMensaje);
        model.addAttribute(MODEL_ATTR_MENSAJE, "Gracias por tu mensaje. Nos pondremos en contacto contigo pronto.");
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensajeDto());
        model.addAttribute(MODEL_ATTR_USUARIO, new UsuarioFormDto());
        return VIEW_USUARIO_CONTACTO;
    }

    @GetMapping("/viajar_public")
    public String viajarPublic(Model model) {
        return "Usuario/viajar_public";
    }

    @GetMapping("/contacto_public")
    public String contactoPublic(Model model) {
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensajeDto());
        return VIEW_USUARIO_INDEX_3_PUBLIC;
    }

    @PostMapping("/contacto_public")
    public String contactoPublicPost(@ModelAttribute ContactoMensajeDto contactoMensajeDto, Model model) {
        ContactoMensaje contactoMensaje = new ContactoMensaje();
        contactoMensaje.setNombre(contactoMensajeDto.getNombre());
        contactoMensaje.setApellido(contactoMensajeDto.getApellido());
        contactoMensaje.setTelefono(contactoMensajeDto.getTelefono());
        contactoMensaje.setEmail(contactoMensajeDto.getEmail());
        contactoMensaje.setMensaje(contactoMensajeDto.getMensaje());

        contactoMensajeService.guardarMensaje(contactoMensaje);
        model.addAttribute(MODEL_ATTR_MENSAJE, "Gracias por tu consulta. Nos pondremos en contacto pronto.");
        model.addAttribute(MODEL_ATTR_CONTACTO_MENSAJE, new ContactoMensajeDto());
        return VIEW_USUARIO_INDEX_3_PUBLIC;
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
                    .toList();
        }

        // Dividir usuarios entre admins y usuarios normales
        List<Usuario> administradores = todos.stream()
                .filter(u -> u.getRole() != null && u.getRole().equalsIgnoreCase(ROLE_ADMIN))
                .sorted(Comparator.comparing(Usuario::getNombres, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Usuario::getApellidos, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<Usuario> usuarios = todos.stream()
                .filter(u -> u.getRole() == null || !u.getRole().equalsIgnoreCase(ROLE_ADMIN))
                .sorted(Comparator.comparing(Usuario::getNombres, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Usuario::getApellidos, Comparator.nullsLast(String::compareTo)))
                .toList();

        model.addAttribute("administradores", administradores);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("buscar", buscar);
        model.addAttribute(MODEL_ATTR_MENSAJE, "Gestión de usuarios");
        return VIEW_ADMIN_GESTIONAR_USUARIOS;
    }

    @GetMapping("/editar-usuario/{id}")
    public String editarUsuario(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioService.getUsuarioById(id);
        if (usuario == null) {
            return REDIRECT_GESTIONAR_USUARIOS;
        }
        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        return "Admin/editar_usuario";
    }

    @PostMapping("/eliminar-usuario/{id}")
    public String eliminarUsuario(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        Usuario u = usuarioService.getUsuarioById(id);
        if (u != null) {
            usuarioService.deleteById(id);
            redirectAttrs.addFlashAttribute(MODEL_ATTR_MENSAJE, "Usuario eliminado correctamente.");
        } else {
            redirectAttrs.addFlashAttribute(MODEL_ATTR_MENSAJE, "Usuario no encontrado.");
        }
        return REDIRECT_GESTIONAR_USUARIOS;
    }

    @PostMapping("/actualizar-usuario")
    public String actualizarUsuario(@ModelAttribute UsuarioFormDto usuarioForm,
                                    @RequestParam(required = false) String newPassword,
                                    Model model,
                                    org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        Usuario existente = usuarioService.getUsuarioById(usuarioForm.getId());
        if (existente == null) {
            String view = gestionarUsuarios("", model);
            model.addAttribute(MODEL_ATTR_ERROR, "Usuario no encontrado.");
            return view;
        }

        Usuario datosActualizados = new Usuario();
        datosActualizados.setNombres(usuarioForm.getNombres());
        datosActualizados.setApellidos(usuarioForm.getApellidos());
        datosActualizados.setEmail(usuarioForm.getEmail());
        usuarioService.updateUsuario(existente.getId(), datosActualizados);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            usuarioService.changePassword(existente.getId(), newPassword);
        }

        redirectAttrs.addFlashAttribute(MODEL_ATTR_MENSAJE, "Usuario actualizado correctamente.");
        return REDIRECT_GESTIONAR_USUARIOS;
    }

    @PostMapping("/admin-crear-usuario")
    public String crearUsuario(@ModelAttribute UsuarioFormDto usuarioForm, Model model) {
        if (usuarioService.findByUsername(usuarioForm.getUsername()) != null) {
            model.addAttribute(MODEL_ATTR_ERROR, "El nombre de usuario ya está en uso");
            model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
            return VIEW_ADMIN_GESTIONAR_USUARIOS;
        }

        if (usuarioRepository.findByEmail(usuarioForm.getEmail()) != null) {
            model.addAttribute(MODEL_ATTR_ERROR, "El email ya está registrado");
            model.addAttribute(MODEL_ATTR_USUARIO, usuarioForm);
            return VIEW_ADMIN_GESTIONAR_USUARIOS;
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(usuarioForm.getUsername());
        usuario.setPassword(usuarioForm.getPassword());
        usuario.setNombres(usuarioForm.getNombres());
        usuario.setApellidos(usuarioForm.getApellidos());
        usuario.setEmail(usuarioForm.getEmail());
        usuario.setRole(ROLE_ADMIN);

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
                    .toList();
        }

        model.addAttribute("totalBuses", busService.getAllBuses().size());
        model.addAttribute(MODEL_ATTR_BUSES, buses);
        model.addAttribute("totalRutas", rutaService.getAllRutas().size());
        model.addAttribute("totalUsuarios", usuarioService.getAllUsuarios().size());
        model.addAttribute("usuariosActivos", usuarioService.getAllUsuarios().size());
        model.addAttribute("buscar", buscar);
        return "Admin/reportes";
    }

    @GetMapping("/consultas")
    public String consultas(Model model) {
        model.addAttribute(MODEL_ATTR_BUSES, busService.getAllBuses());
        return "Usuario/consultas";
    }

    @GetMapping("/historial")
    public String historial(Model model, org.springframework.security.core.Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        Usuario usuario = username != null ? usuarioService.findByUsername(username) : null;
        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        model.addAttribute("viajes", viajeService.getViajesByUsername(username));
        return VIEW_USUARIO_HISTORIAL;
    }

    @GetMapping("/historial/{id}")
    public String historialDetalle(@PathVariable Long id, Model model, org.springframework.security.core.Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        Usuario usuario = username != null ? usuarioService.findByUsername(username) : null;
        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        model.addAttribute("viajes", viajeService.getViajesByUsername(username));

        Viaje viaje = viajeService.getViajeDetalle(id, username);
        if (viaje == null) {
            model.addAttribute("detalleMensaje", "No se encontró el viaje o no tienes permiso para verlo.");
        } else {
            model.addAttribute("detalleId", id);
            model.addAttribute("detalleMensaje", "Detalle del viaje '" + viaje.getNombreRuta() + "' cargado correctamente.");
        }

        return VIEW_USUARIO_HISTORIAL;
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
        redirectAttrs.addFlashAttribute(MODEL_ATTR_MENSAJE, "Mensaje eliminado correctamente.");
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
