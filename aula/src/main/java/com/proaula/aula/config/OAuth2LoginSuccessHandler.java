package com.proaula.aula.config;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.LegalConsentService;
import com.proaula.aula.Service.UsuarioService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final long cookieMaxAgeSeconds;
    private final boolean cookieSecure;
    private final UsuarioService usuarioService;
    private final LegalConsentService legalConsentService;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                     @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs,
                                     @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure,
                                     UsuarioService usuarioService,
                                     LegalConsentService legalConsentService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieMaxAgeSeconds = (int) (expirationMs / 1000);
        this.cookieSecure = cookieSecure;
        this.usuarioService = usuarioService;
        this.legalConsentService = legalConsentService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        var session = request.getSession(false);
        boolean accepted = session != null
            && LegalConsentService.CURRENT_VERSION.equals(session.getAttribute("oauthLegalConsentVersion"))
            && Boolean.TRUE.equals(session.getAttribute("oauthAdultConfirmed"))
            && session.getAttribute("oauthConsentAcceptedAt") instanceof LocalDateTime;
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        if (!accepted || usuario == null) {
            response.sendRedirect("/inicio-de-sesion-mejorado?consentRequired=true");
            return;
        }
        try {
            legalConsentService.recordAcceptance(usuario, LegalConsentService.CURRENT_VERSION, true, "OAUTH2",
                (LocalDateTime) session.getAttribute("oauthConsentAcceptedAt"));
        } catch (IllegalArgumentException exception) {
            throw new ServletException("No se pudo registrar la aceptación legal del acceso social.", exception);
        }
        session.removeAttribute("oauthLegalConsentVersion");
        session.removeAttribute("oauthAdultConfirmed");
        session.removeAttribute("oauthConsentSource");
        session.removeAttribute("oauthConsentAcceptedAt");
        String jwt = jwtTokenProvider.generateToken(authentication);
        ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", jwt)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(cookieMaxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());
        response.sendRedirect("/dashboard");
    }
}
