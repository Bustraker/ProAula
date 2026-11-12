package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.LegalConsentService;
import com.proaula.aula.Service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Controller
public class LegalConsentController {

    private final UsuarioService usuarioService;
    private final LegalConsentService legalConsentService;

    public LegalConsentController(UsuarioService usuarioService, LegalConsentService legalConsentService) {
        this.usuarioService = usuarioService;
        this.legalConsentService = legalConsentService;
    }

    @PostMapping("/oauth2/preauthorize")
    public String preauthorizeOAuth(@RequestParam String provider,
                                    @RequestParam(required = false) Boolean legalConsent,
                                    @RequestParam(required = false) Boolean adultConfirmed,
                                    @RequestParam(required = false) String legalVersion,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        if (!java.util.Set.of("google", "github", "discord", "facebook").contains(provider)
                || !Boolean.TRUE.equals(legalConsent) || !Boolean.TRUE.equals(adultConfirmed)) {
            redirectAttributes.addFlashAttribute("error",
                "Debes aceptar los documentos legales y confirmar que tienes al menos 18 años.");
            return "redirect:/inicio-de-sesion-mejorado";
        }
        try {
            if (!LegalConsentService.CURRENT_VERSION.equals(legalVersion)) {
                throw new IllegalArgumentException("La versión legal cambió. Recarga la página y vuelve a aceptar.");
            }
            var session = request.getSession(true);
            session.setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
            session.setAttribute("oauthAdultConfirmed", Boolean.TRUE);
            session.setAttribute("oauthConsentSource", provider);
            session.setAttribute("oauthConsentAcceptedAt", LocalDateTime.now());
            return "redirect:/oauth2/authorization/" + provider;
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/inicio-de-sesion-mejorado";
        }
    }

    @GetMapping("/legal/consentir")
    public String consentPage(org.springframework.security.core.Authentication authentication, Model model) {
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        model.addAttribute("legalVersion", LegalConsentService.CURRENT_VERSION);
        return "legal-consent";
    }

    @PostMapping("/legal/consentir")
    public String recordConsent(org.springframework.security.core.Authentication authentication,
                                @RequestParam(required = false) Boolean legalConsent,
                                @RequestParam(required = false) Boolean adultConfirmed,
                                @RequestParam(required = false) String legalVersion,
                                RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(legalConsent) || !Boolean.TRUE.equals(adultConfirmed)) {
            redirectAttributes.addFlashAttribute("error",
                "Debes aceptar los documentos legales y confirmar que tienes al menos 18 años.");
            return "redirect:/legal/consentir";
        }
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        try {
            legalConsentService.recordAcceptance(usuario, legalVersion, true, "RECONSENT");
            redirectAttributes.addFlashAttribute("success", "Tu aceptación fue registrada.");
            return "redirect:/perfil";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/legal/consentir";
        }
    }

    @PostMapping("/legal/revocar")
    public String revokeConsent(Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        boolean revoked = legalConsentService.revokeCurrentConsent(authentication.getName());
        new SecurityContextLogoutHandler().logout(request, response, authentication);

        for (String cookieName : List.of("JWT_TOKEN", "BustrakerSession", "JSESSIONID")) {
            ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        }

        return revoked
            ? "redirect:/inicio-de-sesion-mejorado?consentRevoked=true"
            : "redirect:/inicio-de-sesion-mejorado?consentRevocationMissing=true";
    }
}
