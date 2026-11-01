package com.proaula.aula.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    // This process-local limit uses the socket peer address, not client-controlled forwarding headers.
    private final Map<String, AttemptWindow> attemptsByAddress = new HashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!isAuthenticationRequest(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if ((isFormLogin(path) || path.startsWith("/admin/"))
                && !hasValidFormCredentials(request, path)) {
            String destination = path.startsWith("/admin/")
                    ? "/admin-login?error=datos-invalidos"
                    : "/inicio-de-sesion-mejorado?error=true";
            response.sendRedirect(request.getContextPath() + destination);
            return;
        }

        if (isRateLimited(request.getRemoteAddr(), path)) {
            response.setHeader("Retry-After", "60");
            if (isFormRequest(path)) {
                String destination = path.startsWith("/admin/")
                        ? "/admin-login?rateLimited=true"
                        : "/registro".equals(path)
                            ? "/registro-mejorado?rateLimited=true"
                            : "/inicio-de-sesion-mejorado?rateLimited=true";
                response.sendRedirect(request.getContextPath() + destination);
                return;
            }

            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Demasiados intentos. Espera un minuto.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticationRequest(HttpServletRequest request, String path) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && ("/login".equals(path)
                    || "/api/usuarios/login".equals(path)
                    || "/registro".equals(path)
                    || "/api/usuarios/register".equals(path)
                    || "/admin/login".equals(path)
                    || "/admin/verificar-codigo".equals(path)
                    || "/admin/registrar".equals(path));
    }

    private boolean isFormLogin(String path) {
        return "/login".equals(path);
    }

    private boolean isFormRequest(String path) {
        return "/login".equals(path) || "/registro".equals(path)
                || path.startsWith("/admin/");
    }

    private boolean hasValidFormCredentials(HttpServletRequest request, String path) {
        if ("/admin/verificar-codigo".equals(path)) {
            String code = request.getParameter("codigoAdmin");
            return code != null && !code.isBlank() && code.length() <= 256;
        }

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || username.isBlank() || username.length() > 20
                || password == null || password.isEmpty() || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        if ("/admin/registrar".equals(path)) {
            String confirmation = request.getParameter("passwordConfirm");
            return confirmation != null && confirmation.length() <= 72;
        }
        return true;
    }

    private synchronized boolean isRateLimited(String remoteAddress, String path) {
        long now = System.nanoTime();
        String key = remoteAddress + "|" + rateLimitCategory(path);
        AttemptWindow current = attemptsByAddress.get(key);
        if (current == null || now - current.startedAtNanos() >= WINDOW_NANOS) {
            if (current == null && attemptsByAddress.size() >= MAX_TRACKED_ADDRESSES) {
                removeExpiredWindows(now);
                if (attemptsByAddress.size() >= MAX_TRACKED_ADDRESSES) {
                    return true;
                }
            }
            attemptsByAddress.put(key, new AttemptWindow(now, 1));
            return false;
        }
        if (current.attempts() >= MAX_ATTEMPTS) {
            return true;
        }
        attemptsByAddress.put(key,
                new AttemptWindow(current.startedAtNanos(), current.attempts() + 1));
        return false;
    }

    private String rateLimitCategory(String path) {
        if ("/login".equals(path) || "/api/usuarios/login".equals(path)) {
            return "user-login";
        }
        if ("/admin/login".equals(path) || "/admin/verificar-codigo".equals(path)) {
            return "admin-login";
        }
        return "registration";
    }

    private void removeExpiredWindows(long now) {
        Iterator<AttemptWindow> iterator = attemptsByAddress.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().startedAtNanos() >= WINDOW_NANOS) {
                iterator.remove();
            }
        }
    }

    private record AttemptWindow(long startedAtNanos, int attempts) {
    }
}
