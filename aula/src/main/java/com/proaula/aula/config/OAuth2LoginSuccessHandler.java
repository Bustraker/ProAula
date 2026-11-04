package com.proaula.aula.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final long cookieMaxAgeSeconds;
    private final boolean cookieSecure;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                     @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs,
                                     @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this.jwtTokenProvider = jwtTokenProvider;
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
        response.sendRedirect("/dashboard");
    }
}
