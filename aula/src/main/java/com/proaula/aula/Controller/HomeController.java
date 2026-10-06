package com.proaula.aula.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Entity.Viaje;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.ContactoMensajeDto;
import com.proaula.aula.dto.BusConsultaDto;
import com.proaula.aula.dto.BusConsultaMapper;
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
        return prepararGestionUsuarios(buscar, model, null);
    }

    private String prepararGestionUsuarios(String buscar, Model model, UsuarioFormDto nuevoUsuario) {
        List<Usuario> cuentas = usuarioService.getAllUsuarios();
        String consulta = buscar == null ? "" : buscar.trim();
        List<Usuario> coincidencias = cuentas;

        if (!consulta.isEmpty()) {
            String consultaNormalizada = consulta.toLowerCase(Locale.ROOT);
            coincidencias = cuentas.stream()
                    .filter(usuario -> contieneTexto(usuario.getNombres(), consultaNormalizada)
                            || contieneTexto(usuario.getApellidos(), consultaNormalizada)
                            || contieneTexto(usuario.getEmail(), consultaNormalizada)
                            || contieneTexto(usuario.getUsername(), consultaNormalizada)
                            || contieneTexto(usuario.getRole(), consultaNormalizada))
                    .toList();
        }

        Comparator<Usuario> porNombre = Comparator
                .comparing((Usuario usuario) -> usuario.getNombres(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(usuario -> usuario.getApellidos(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        List<Usuario> administradores = coincidencias.stream()
                .filter(usuario -> usuario.getRole() != null && usuario.getRole().equalsIgnoreCase(ROLE_ADMIN))
                .sorted(porNombre)
                .toList();
        List<Usuario> usuarios = coincidencias.stream()
                .filter(usuario -> usuario.getRole() == null || !usuario.getRole().equalsIgnoreCase(ROLE_ADMIN))
                .sorted(porNombre)
                .toList();
        long totalAdministradores = cuentas.stream()
                .filter(usuario -> usuario.getRole() != null && usuario.getRole().equalsIgnoreCase(ROLE_ADMIN))
                .count();

        model.addAttribute("administradores", administradores);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("buscar", consulta);
        model.addAttribute("totalCuentas", cuentas.size());
        model.addAttribute("totalCoincidencias", coincidencias.size());
        model.addAttribute("totalAdministradores", totalAdministradores);
        model.addAttribute("totalUsuarios", cuentas.size() - totalAdministradores);
        model.addAttribute("nuevoUsuario", nuevoUsuario == null ? new UsuarioFormDto() : nuevoUsuario);
        return VIEW_ADMIN_GESTIONAR_USUARIOS;
    }

    private boolean contieneTexto(String valor, String consultaNormalizada) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(consultaNormalizada);
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
            return prepararGestionUsuarios("", model, usuarioForm);
        }

        if (usuarioRepository.findByEmail(usuarioForm.getEmail()) != null) {
            model.addAttribute(MODEL_ATTR_ERROR, "El email ya está registrado");
            return prepararGestionUsuarios("", model, usuarioForm);
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
        List<Bus> todosLosBuses = busService.getAllBuses();
        List<Bus> buses = todosLosBuses;
        String termino = buscar == null ? "" : buscar.trim();

        if (!termino.isEmpty()) {
            String terminoNormalizado = termino.toLowerCase(Locale.ROOT);
            buses = todosLosBuses.stream()
                    .filter(bus -> (bus.getPlaca() != null
                            && bus.getPlaca().toLowerCase(Locale.ROOT).contains(terminoNormalizado))
                            || (bus.getModelo() != null
                            && bus.getModelo().toLowerCase(Locale.ROOT).contains(terminoNormalizado)))
                    .toList();
        }

        int totalBuses = todosLosBuses.size();
        int totalUsuarios = usuarioService.getAllUsuarios().size();
        int totalRutas = rutaService.getAllRutas().size();

        model.addAttribute("totalBuses", totalBuses);
        model.addAttribute(MODEL_ATTR_BUSES, buses);
        model.addAttribute("totalRutas", totalRutas);
        model.addAttribute("totalUsuarios", totalUsuarios);
        model.addAttribute("resultadosBuses", buses.size());
        model.addAttribute("buscar", termino);
        return "Admin/reportes";
    }

    @GetMapping("/consultas")
    public String consultas(Model model, Authentication authentication) {
        boolean autenticado = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        List<Bus> todosLosBuses = busService.getAllBuses();
        List<BusConsultaDto> buses = autenticado
                ? BusConsultaMapper.authenticatedBuses(todosLosBuses)
                : BusConsultaMapper.publicRoutes(todosLosBuses);
        long rutasConBuses = buses.stream()
                .map(bus -> bus.getRutaNombre())
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .count();
        long barriosCubiertos = buses.stream()
                .flatMap(bus -> bus.getBarrios().stream())
                .distinct()
                .count();
        model.addAttribute("autenticado", autenticado);
        model.addAttribute(MODEL_ATTR_BUSES, buses);
        model.addAttribute("totalBuses", buses.size());
        model.addAttribute("rutasConBuses", rutasConBuses);
        model.addAttribute("barriosCubiertos", barriosCubiertos);
        return "Usuario/consultas";
    }

    @GetMapping("/historial")
    public String historial(Model model, org.springframework.security.core.Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        Usuario usuario = username != null ? usuarioService.findByUsername(username) : null;
        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        addHistorySummary(model, username);
        return VIEW_USUARIO_HISTORIAL;
    }

    @GetMapping("/historial/{id}")
    public String historialDetalle(@PathVariable Long id, Model model, org.springframework.security.core.Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        Usuario usuario = username != null ? usuarioService.findByUsername(username) : null;
        model.addAttribute(MODEL_ATTR_USUARIO, usuario);
        addHistorySummary(model, username);

        model.addAttribute("detalleId", id);
        Viaje viaje = username != null ? viajeService.getViajeDetalle(id, username) : null;
        if (viaje == null) {
            model.addAttribute("detalleEncontrado", false);
            model.addAttribute("detalleMensaje", "No se encontró el viaje o no tienes permiso para verlo.");
        } else {
            model.addAttribute("detalleEncontrado", true);
            model.addAttribute("detalleMensaje", "Detalle del viaje '" + viaje.getNombreRuta() + "' cargado correctamente.");
        }

        return VIEW_USUARIO_HISTORIAL;
    }

    private void addHistorySummary(Model model, String username) {
        List<Viaje> viajes = username != null
                ? viajeService.getViajesByUsername(username)
                : java.util.Collections.emptyList();
        model.addAttribute("viajes", viajes);
        model.addAttribute("viajesTotal", viajes.size());
        model.addAttribute("viajesCompletados", countViajesWithStatus(viajes, "Completado"));
        model.addAttribute("viajesPendientes", countViajesWithStatus(viajes, "Pendiente"));
        model.addAttribute("viajesCancelados", countViajesWithStatus(viajes, "Cancelado"));
    }

    private long countViajesWithStatus(List<Viaje> viajes, String estado) {
        return viajes.stream()
                .filter(viaje -> viaje.getEstado() != null && estado.equalsIgnoreCase(viaje.getEstado().trim()))
                .count();
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
