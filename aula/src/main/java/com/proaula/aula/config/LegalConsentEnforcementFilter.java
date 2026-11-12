package com.proaula.aula.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.proaula.aula.Service.LegalConsentService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class LegalConsentEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_EXACT_PATHS = Set.of(
        "/", "/inicio-de-sesion-mejorado", "/inicio-de-sesion-mejorado.html", "/inicio_de_sesion",
        "/registro", "/registro-mejorado", "/registro-mejorado.html",
        "/admin-login", "/admin/verificar-codigo", "/admin/login", "/admin/registrar",
        "/oauth2/preauthorize", "/oauth2/consentir", "/legal/consentir", "/legal/revocar",
        "/terminos", "/terminos.html", "/privacidad", "/privacidad.html",
        "/contacto", "/contacto_public", "/contacto_usuario",
        "/logout", "/error", "/favicon.ico", "/actuator/health"
    );

    private final LegalConsentService legalConsentService;

    public LegalConsentEnforcementFilter(LegalConsentService legalConsentService) {
        this.legalConsentService = legalConsentService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = applicationPath(request);
        if (isExempt(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || legalConsentService.hasCurrentConsent(authentication.getName())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"LEGAL_CONSENT_REQUIRED\",\"message\":\"Debes aceptar la versión vigente de los documentos legales para continuar.\"}");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/legal/consentir");
    }

    private String applicationPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }

    private boolean isExempt(String path, String method) {
        return ALLOWED_EXACT_PATHS.contains(path)
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/images/")
            || path.startsWith("/data/cartografia/")
            || path.startsWith("/login/oauth2/")
            || path.startsWith("/oauth2/authorization/")
            || ("GET".equalsIgnoreCase(method) && path.startsWith("/api/barrios/"))
            || ("GET".equalsIgnoreCase(method)
                && ("/api/rutas".equals(path) || "/api/rutas-con-coordenadas".equals(path)
                    || path.startsWith("/api/incidentes/ruta/")
                    || path.startsWith("/api/llegadas/") || "/api/map/tiles".equals(path)))
            || path.startsWith("/rutas/")
            || path.startsWith("/ruta/")
            || path.startsWith("/buses/")
            || path.startsWith("/public_index_3")
            || path.startsWith("/index_3_public")
            || path.startsWith("/viajar_public");
    }
}
