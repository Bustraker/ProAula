package com.proaula.aula.config;

import com.proaula.aula.Service.LegalConsentService;
import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuthConsentGateFilter extends OncePerRequestFilter {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "github", "discord", "facebook");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        String path = requestUri.substring(contextPath.length());
        if (path.startsWith("/oauth2/authorization/")) {
            var session = request.getSession(false);
            boolean accepted = session != null
                && LegalConsentService.CURRENT_VERSION.equals(session.getAttribute("oauthLegalConsentVersion"))
                && Boolean.TRUE.equals(session.getAttribute("oauthAdultConfirmed"))
                && session.getAttribute("oauthConsentAcceptedAt") instanceof java.time.LocalDateTime
                && providerMatches(path, session.getAttribute("oauthConsentSource"));
            if (!accepted) {
                String provider = path.substring("/oauth2/authorization/".length());
                String destination = SUPPORTED_PROVIDERS.contains(provider)
                    ? "/oauth2/consentir?provider=" + provider
                    : "/inicio-de-sesion-mejorado";
                response.sendRedirect(request.getContextPath() + destination);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean providerMatches(String path, Object consentSource) {
        String provider = path.substring("/oauth2/authorization/".length());
        return consentSource instanceof String source && provider.equals(source);
    }
}
