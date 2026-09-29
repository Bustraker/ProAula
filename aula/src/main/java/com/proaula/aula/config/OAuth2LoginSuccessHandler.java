package com.proaula.aula.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioService usuarioService;
    private final long cookieMaxAgeSeconds;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                     UsuarioService usuarioService,
                                     @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.usuarioService = usuarioService;
        this.cookieMaxAgeSeconds = (int) (expirationMs / 1000);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String jwt = jwtTokenProvider.generateToken(authentication);
        Cookie tokenCookie = new Cookie("JWT_TOKEN", jwt);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(request.isSecure());
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge((int) cookieMaxAgeSeconds);
        response.addCookie(tokenCookie);
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        response.sendRedirect(usuario != null && usuario.isPasswordSetupRequired()
            ? "/oauth2/complete-password" : "/dashboard");
    }
}
