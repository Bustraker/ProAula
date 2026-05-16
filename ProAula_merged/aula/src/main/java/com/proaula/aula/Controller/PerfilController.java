package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PerfilController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @GetMapping("/perfil")
    public String perfil(Model model, Authentication authentication) {
        String username = authentication.getName();
        Usuario usuario = usuarioService.findByUsername(username);

        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado?error=usuario_no_encontrado";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("rutasFavoritas", 0);
        model.addAttribute("viajesCompletados", 0);
        model.addAttribute("fechaRegistro", "2026");

        return "perfil-usuario";
    }

    @PostMapping("/perfil/actualizar")
    public String actualizarPerfil(@RequestParam String nombres,
                                   @RequestParam String apellidos,
                                   @RequestParam String email,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Usuario usuario = usuarioService.findByUsername(username);

            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Usuario no encontrado");
                return "redirect:/perfil";
            }

            // FIX: usar updateUsuario() en vez de register() para no re-encriptar la contraseña
            usuarioService.updateUsuario(usuario.getId(), buildUsuarioUpdate(nombres, apellidos, email));

            redirectAttributes.addFlashAttribute("success", "✅ Perfil actualizado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "❌ Error al actualizar el perfil: " + e.getMessage());
        }

        return "redirect:/perfil";
    }

    @PostMapping("/perfil/cambiar-password")
    public String cambiarPassword(@RequestParam String passwordActual,
                                  @RequestParam String passwordNuevo,
                                  @RequestParam String passwordConfirmar,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Usuario usuario = usuarioService.findByUsername(username);

            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Usuario no encontrado");
                return "redirect:/perfil";
            }

            if (passwordActual == null || passwordActual.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "❌ La contraseña actual es requerida");
                return "redirect:/perfil";
            }

            if (passwordNuevo == null || passwordNuevo.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "❌ La nueva contraseña es requerida");
                return "redirect:/perfil";
            }

            if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "❌ La contraseña actual es incorrecta");
                return "redirect:/perfil";
            }

            if (!passwordNuevo.equals(passwordConfirmar)) {
                redirectAttributes.addFlashAttribute("error", "❌ Las nuevas contraseñas no coinciden");
                return "redirect:/perfil";
            }

            if (passwordEncoder.matches(passwordNuevo, usuario.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "❌ La nueva contraseña debe ser diferente a la actual");
                return "redirect:/perfil";
            }

            if (passwordNuevo.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "❌ La contraseña debe tener al menos 6 caracteres");
                return "redirect:/perfil";
            }

            // FIX: usar changePassword() en vez de register() para no re-encriptar doble
            usuarioService.changePassword(usuario.getId(), passwordNuevo);

            redirectAttributes.addFlashAttribute("success", "✅ Contraseña cambiada exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "❌ Error al cambiar la contraseña: " + e.getMessage());
        }

        return "redirect:/perfil";
    }

    private Usuario buildUsuarioUpdate(String nombres, String apellidos, String email) {
        Usuario u = new Usuario();
        u.setNombres(nombres);
        u.setApellidos(apellidos);
        u.setEmail(email);
        return u;
    }
}
