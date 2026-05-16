package com.proaula.aula.Controller;

import com.proaula.aula.Service.AdminCodeService;
import com.proaula.aula.Service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collection;

@Controller
public class AdminLoginController {
    
    private static final Logger log = LoggerFactory.getLogger(AdminLoginController.class);
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private AdminCodeService adminCodeService;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private SecurityContextRepository securityContextRepository;
    
    /**
     * Mostrar formulario de login administrativo
     */
    @GetMapping("/admin-login")
    public String mostrarLoginAdmin(
            @RequestParam(required = false) Boolean verified,
            HttpSession session,
            Model model) {
        
        Boolean verifiedSession = (Boolean) session.getAttribute("adminCodeVerified");
        boolean isVerified = (verified != null && verified) || Boolean.TRUE.equals(verifiedSession);
        model.addAttribute("verified", isVerified);
        
        return "admin-login";
    }
    
    /**
     * Paso 1: Verificar código de administrador
     */
    @PostMapping("/admin/verificar-codigo")
    public String verificarCodigoAdmin(
            @RequestParam String codigoAdmin,
            RedirectAttributes redirectAttributes,
            HttpSession session) {
        
        if (adminCodeService.isValidAdminCode(codigoAdmin)) {
            session.setAttribute("adminCodeVerified", true);
            redirectAttributes.addFlashAttribute("verified", true);
            return "redirect:/admin-login?verified=true";
        } else {
            redirectAttributes.addFlashAttribute("error", "❌ Código de administrador incorrecto");
            session.removeAttribute("adminCodeVerified");
            return "redirect:/admin-login";
        }
    }
    
    /**
     * Paso 2: Autenticar con credenciales de usuario ADMIN
     */
    @PostMapping("/admin/login")
    public String loginAdmin(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        
        Boolean codigoVerificado = (Boolean) session.getAttribute("adminCodeVerified");
        if (!Boolean.TRUE.equals(codigoVerificado)) {
            redirectAttributes.addFlashAttribute("error", "❌ Debe verificar el código de administrador primero");
            return "redirect:/admin-login";
        }
        
        try {
            // Validar primero en la BD que el usuario sea ADMIN
            if (!usuarioService.isAdminUser(username)) {
                log.warn("Intento de login de admin con usuario no-admin: {}", username);
                redirectAttributes.addFlashAttribute("error", "❌ Este usuario no tiene permisos de administrador");
                return "redirect:/admin-login?verified=true";
            }
            
            // Autenticar con Spring Security
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );
            
            // Doble verificación: validar que el usuario tenga rol ADMIN en authorities
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            boolean isAdmin = authorities.stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
            
            if (!isAdmin) {
                // Usuario no es administrador
                log.error("Usuario {} autenticado pero sin rol ADMIN. Roles obtenidos: {}", username, authorities);
                redirectAttributes.addFlashAttribute("error", "❌ Error de roles: Este usuario no tiene permisos de administrador");
                return "redirect:/admin-login?verified=true";
            }
            
            // ✅ IMPORTANTE: Persistir la autenticación en Spring Security y en sesión HTTP
            // Crear contexto de seguridad
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            
            // Establecer en SecurityContextHolder
            SecurityContextHolder.setContext(context);
            
            // Persistir en la sesión HTTP usando SecurityContextRepository
            securityContextRepository.saveContext(context, request, response);
            
            // Guardar información adicional en sesión
            session.setAttribute("usuarioAutenticado", username);
            session.setAttribute("rolUsuario", "ADMIN");
            session.removeAttribute("adminCodeVerified"); // Limpiar código verificado
            
            log.info("✅ Admin login successful for user: {}, redirecting to /index_2", username);
            log.info("✅ Authentication persisted in session. Roles: {}", authorities);
            
            // Redirigir al dashboard de administrador
            return "redirect:/index_2";
            
        } catch (BadCredentialsException e) {
            log.warn("Intento de login fallido para usuario: {} - Credenciales inválidas", username);
            redirectAttributes.addFlashAttribute("error", "❌ Usuario o contraseña incorrectos");
            return "redirect:/admin-login?verified=true";
        } catch (Exception e) {
            log.error("Error al iniciar sesión como admin para usuario: {}", username, e);
            redirectAttributes.addFlashAttribute("error", "❌ Error al iniciar sesión: " + e.getMessage());
            return "redirect:/admin-login?verified=true";
        }
    }
    
    /**
     * Logout de administrador
     */
    @GetMapping("/admin/logout")
    public String logoutAdmin(HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            SecurityContextHolder.clearContext();
            session.removeAttribute("adminCodeVerified");
            session.invalidate();
            redirectAttributes.addFlashAttribute("success", "✅ Sesión cerrada correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cerrar sesión");
        }
        return "redirect:/admin-login";
    }
}
