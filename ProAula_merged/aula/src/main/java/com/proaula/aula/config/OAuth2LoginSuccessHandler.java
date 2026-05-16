package com.proaula.aula.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final long cookieMaxAgeSeconds;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                     @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieMaxAgeSeconds = (int) (expirationMs / 1000);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String jwt = jwtTokenProvider.generateToken(authentication);
        Cookie tokenCookie = new Cookie("JWT_TOKEN", jwt);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge((int) cookieMaxAgeSeconds);
        response.addCookie(tokenCookie);
        response.sendRedirect("/dashboard");
    }
}
