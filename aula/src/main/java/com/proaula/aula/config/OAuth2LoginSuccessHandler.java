package com.proaula.aula.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioService usuarioService;
    private final long cookieMaxAgeSeconds;
    private final boolean cookieSecure;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                     UsuarioService usuarioService,
                                     @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs,
                                     @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.usuarioService = usuarioService;
        this.cookieMaxAgeSeconds = (int) (expirationMs / 1000);
        this.cookieSecure = cookieSecure;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String jwt = jwtTokenProvider.generateToken(authentication);
        ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", jwt)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(cookieMaxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());
        Usuario usuario = usuarioService.findByUsername(authentication.getName());
        response.sendRedirect(usuario != null && usuario.isPasswordSetupRequired()
            ? "/oauth2/complete-password" : "/dashboard");
    }
}
