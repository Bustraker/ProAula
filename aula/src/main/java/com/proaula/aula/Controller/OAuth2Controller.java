package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OAuth2Controller {

    private final UsuarioService usuarioService;

    public OAuth2Controller(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/oauth2/complete-password")
    public String completePasswordPage(Authentication authentication, Model model) {
        if (authentication == null || authentication.getName() == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        model.addAttribute("username", authentication.getName());
        return "oauth2-complete-password";
    }

    @PostMapping("/oauth2/complete-password")
    public String completePassword(@RequestParam String password,
                                   @RequestParam String confirmPassword,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        if (authentication == null || authentication.getName() == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        if (password == null || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/oauth2/complete-password";
        }
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
            return "redirect:/oauth2/complete-password";
        }

        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado. Vuelve a iniciar sesión.");
            return "redirect:/inicio-de-sesion-mejorado";
        }
        usuarioService.completePasswordSetup(authentication.getName(), password);
        return "redirect:/dashboard";
    }
}
