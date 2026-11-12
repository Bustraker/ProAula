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
import java.util.Set;

@Controller
public class LegalConsentController {

    private final UsuarioService usuarioService;
    private final LegalConsentService legalConsentService;

    public LegalConsentController(UsuarioService usuarioService, LegalConsentService legalConsentService) {
        this.usuarioService = usuarioService;
        this.legalConsentService = legalConsentService;
    }

    @GetMapping("/oauth2/consentir")
    public String oauthConsentPage(@RequestParam String provider,
                                   HttpServletRequest request,
                                   Model model) {
        if (!Set.of("google", "github", "discord", "facebook").contains(provider)) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        request.getSession(true).setAttribute("oauthConsentProvider", provider);
        model.addAttribute("provider", provider);
        model.addAttribute("providerLabel", provider.substring(0, 1).toUpperCase() + provider.substring(1));
        model.addAttribute("legalVersion", LegalConsentService.CURRENT_VERSION);
        return "oauth-consent";
    }

    @PostMapping("/oauth2/preauthorize")
    public String preauthorizeOAuth(@RequestParam(required = false) String provider,
                                    @RequestParam(required = false) Boolean legalConsent,
                                    @RequestParam(required = false) Boolean adultConfirmed,
                                    @RequestParam(required = false) String legalVersion,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        var session = request.getSession(false);
        String selectedProvider = session == null
            ? null
            : (String) session.getAttribute("oauthConsentProvider");
        if (provider == null || !Set.of("google", "github", "discord", "facebook").contains(provider)
                || !provider.equals(selectedProvider)) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        if (!Boolean.TRUE.equals(legalConsent) || !Boolean.TRUE.equals(adultConfirmed)) {
            redirectAttributes.addFlashAttribute("error",
                "Debes aceptar los documentos legales y confirmar que tienes al menos 18 años.");
            return "redirect:/oauth2/consentir?provider=" + provider;
        }
        try {
            if (!LegalConsentService.CURRENT_VERSION.equals(legalVersion)) {
                throw new IllegalArgumentException("La versión legal cambió. Recarga la página y vuelve a aceptar.");
            }
            session.setAttribute("oauthLegalConsentVersion", LegalConsentService.CURRENT_VERSION);
            session.setAttribute("oauthAdultConfirmed", Boolean.TRUE);
            session.setAttribute("oauthConsentSource", provider);
            session.setAttribute("oauthConsentAcceptedAt", LocalDateTime.now());
            session.removeAttribute("oauthConsentProvider");
            return "redirect:/oauth2/authorization/" + provider;
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/oauth2/consentir?provider=" + provider;
        }
    }

    @GetMapping("/legal/consentir")
    public String consentPage(org.springframework.security.core.Authentication authentication, Model model) {
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        if (legalConsentService.hasCurrentConsent(authentication.getName())) {
            return "redirect:/perfil";
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
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (usuario == null) {
            return "redirect:/inicio-de-sesion-mejorado";
        }
        if (legalConsentService.hasCurrentConsent(authentication.getName())) {
            return "redirect:/perfil";
        }
        if (!Boolean.TRUE.equals(legalConsent) || !Boolean.TRUE.equals(adultConfirmed)) {
            redirectAttributes.addFlashAttribute("error",
                "Debes aceptar los documentos legales y confirmar que tienes al menos 18 años.");
            return "redirect:/legal/consentir";
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
