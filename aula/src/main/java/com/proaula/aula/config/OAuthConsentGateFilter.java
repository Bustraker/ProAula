package com.proaula.aula.config;

import com.proaula.aula.Service.LegalConsentService;
import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuthConsentGateFilter extends OncePerRequestFilter {

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
                && session.getAttribute("oauthConsentAcceptedAt") instanceof java.time.LocalDateTime;
            if (!accepted) {
                response.sendRedirect("/inicio-de-sesion-mejorado?consentRequired=true");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
