package com.proaula.aula.Controller;

import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.AdminCodeRequestService;
import com.proaula.aula.Service.AdminCodeService;
import com.proaula.aula.Service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final String SESSION_CODE_ID = "adminCodeId";
    private static final String SESSION_REQUEST_ID = "adminRegistrationRequestId";

    private final UsuarioService usuarioService;
    private final AdminCodeService adminCodeService;
    private final AdminCodeRequestService requestService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AdminLoginController(UsuarioService usuarioService,
                                AdminCodeService adminCodeService,
                                AdminCodeRequestService requestService,
                                AuthenticationManager authenticationManager,
                                SecurityContextRepository securityContextRepository) {
        this.usuarioService = usuarioService;
        this.adminCodeService = adminCodeService;
        this.requestService = requestService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/admin-login")
    public String mostrarLoginAdmin(HttpSession session, Model model) {
        Long codeId = longSessionAttribute(session, SESSION_CODE_ID);
        AdminCode verifiedCode = codeId == null ? null : adminCodeService.findById(codeId).orElse(null);
        boolean verified = Boolean.TRUE.equals(session.getAttribute("adminCodeVerified"))
            && verifiedCode != null;
        boolean registrationPending = false;

        if (verified && verifiedCode.getRegistrationRequestId() != null) {
            AdminCodeRequest request = requestService.findById(verifiedCode.getRegistrationRequestId());
            registrationPending = request.getStatus() == AdminCodeRequest.Status.APPROVED;
            if (request.getStatus() != AdminCodeRequest.Status.APPROVED
                    && request.getStatus() != AdminCodeRequest.Status.REGISTERED) {
                clearCodeVerification(session);
                verified = false;
            } else {
                model.addAttribute("candidateName", request.getNombres() + " " + request.getApellidos());
                model.addAttribute("candidateEmail", request.getEmail());
            }
        }

        if (!verified) {
            clearCodeVerification(session);
        }
        model.addAttribute("verified", verified);
        model.addAttribute("registrationPending", registrationPending);
        return "admin-login";
    }

    @PostMapping("/admin/verificar-codigo")
    public String verificarCodigoAdmin(@RequestParam String codigoAdmin,
                                      RedirectAttributes redirectAttributes,
                                      HttpSession session) {
        AdminCode code = adminCodeService.findByCodigo(codigoAdmin);
        if (code != null && isAvailableCode(code)) {
            session.setAttribute("adminCodeVerified", true);
            session.setAttribute(SESSION_CODE_ID, code.getId());
            if (code.getRegistrationRequestId() != null) {
                AdminCodeRequest request = requestService.findById(code.getRegistrationRequestId());
                if (request.getStatus() == AdminCodeRequest.Status.APPROVED) {
                    session.setAttribute(SESSION_REQUEST_ID, request.getId());
                } else {
                    session.removeAttribute(SESSION_REQUEST_ID);
                }
            } else {
                session.removeAttribute(SESSION_REQUEST_ID);
            }
            return "redirect:/admin-login?verified=true";
        }

        clearCodeVerification(session);
        redirectAttributes.addFlashAttribute("error", "El código no es válido o no está autorizado.");
        return "redirect:/admin-login";
    }

    @PostMapping("/admin/registrar")
    public String completarRegistroAdmin(@RequestParam String username,
                                         @RequestParam String password,
                                         @RequestParam String passwordConfirm,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        Long requestId = longSessionAttribute(session, SESSION_REQUEST_ID);
        Long codeId = verifiedCodeId(session);
        if (requestId == null || codeId == null) {
            clearCodeVerification(session);
            redirectAttributes.addFlashAttribute("error", "Verifica primero el código de invitación recibido por correo.");
            return "redirect:/admin-login";
        }
        if (!password.equals(passwordConfirm)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
            return "redirect:/admin-login?verified=true";
        }

        try {
            requestService.completeRegistration(requestId, codeId, username, password);
            session.removeAttribute(SESSION_REQUEST_ID);
            redirectAttributes.addFlashAttribute("success",
                "Tu cuenta ADMIN quedó registrada. Ingresa con el usuario y la contraseña que acabas de crear.");
            return "redirect:/admin-login?verified=true";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin-login?verified=true";
        } catch (DataIntegrityViolationException exception) {
            log.warn("Admin account creation conflicted with an existing database record for request {}.", requestId);
            redirectAttributes.addFlashAttribute("error",
                "El usuario o correo ya se encuentra registrado. Verifica los datos o contacta al responsable.");
            return "redirect:/admin-login?verified=true";
        }
    }

    @PostMapping("/admin/login")
    public String loginAdmin(@RequestParam String username,
                             @RequestParam String password,
                             HttpSession session,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             RedirectAttributes redirectAttributes) {
        Long codeId = verifiedCodeId(session);
        if (codeId == null) {
            redirectAttributes.addFlashAttribute("error", "Debes verificar el código de administrador primero.");
            return "redirect:/admin-login";
        }
        if (longSessionAttribute(session, SESSION_REQUEST_ID) != null) {
            redirectAttributes.addFlashAttribute("error", "Completa primero el registro de tu cuenta ADMIN.");
            return "redirect:/admin-login?verified=true";
        }

        AdminCode code = adminCodeService.findById(codeId).orElse(null);
        Usuario user = usuarioService.findByUsername(username);
        if (code == null || user == null || !usuarioService.isAdminUser(username)
                || (code.getEmail() != null && !code.getEmail().equalsIgnoreCase(user.getEmail()))) {
            log.warn("Intento de acceso ADMIN con un código o cuenta no autorizados.");
            redirectAttributes.addFlashAttribute("error", "El código o las credenciales no son válidos.");
            return "redirect:/admin-login?verified=true";
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            boolean isAdmin = authorities.stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            if (!isAdmin) {
                redirectAttributes.addFlashAttribute("error", "Esta cuenta no tiene permisos de administrador.");
                return "redirect:/admin-login?verified=true";
            }

            clearCodeVerification(session);
            session.invalidate();
            HttpSession authenticatedSession = request.getSession(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            authenticatedSession.setAttribute("usuarioAutenticado", username);
            authenticatedSession.setAttribute("rolUsuario", "ADMIN");
            return "redirect:/index_2";
        } catch (AuthenticationException exception) {
            log.warn("Login administrativo fallido para el usuario indicado.");
            redirectAttributes.addFlashAttribute("error", "El código o las credenciales no son válidos.");
            return "redirect:/admin-login?verified=true";
        }
    }

    private boolean isAvailableCode(AdminCode code) {
        if (code.getRegistrationRequestId() == null) {
            return true;
        }
        AdminCodeRequest request = requestService.findById(code.getRegistrationRequestId());
        return request.getStatus() == AdminCodeRequest.Status.APPROVED
            || request.getStatus() == AdminCodeRequest.Status.REGISTERED;
    }

    private Long verifiedCodeId(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("adminCodeVerified"))) {
            return null;
        }
        Long codeId = longSessionAttribute(session, SESSION_CODE_ID);
        return codeId != null && adminCodeService.findById(codeId).isPresent() ? codeId : null;
    }

    private Long longSessionAttribute(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        return value instanceof Long id ? id : null;
    }

    private void clearCodeVerification(HttpSession session) {
        session.removeAttribute("adminCodeVerified");
        session.removeAttribute(SESSION_CODE_ID);
        session.removeAttribute(SESSION_REQUEST_ID);
    }
}
