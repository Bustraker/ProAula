package com.proaula.aula.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(-100)
public class AccessibilityResourceFilter extends OncePerRequestFilter {

    private static final String STYLE_TAG =
            "<link rel=\"stylesheet\" href=\"/css/accessibility.css\">";
    private static final String SCRIPT_TAG =
            "<script src=\"/js/accessibility.js\" defer></script>";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper cachingResponse =
                new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, cachingResponse);

        String contentType = cachingResponse.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("text/html")) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        byte[] originalBytes = cachingResponse.getContentAsByteArray();
        if (originalBytes.length == 0) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        String html = new String(originalBytes, StandardCharsets.UTF_8);
        if (!html.contains("</html>")) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        if (!html.contains("/css/accessibility.css")) {
            if (html.contains("</head>")) {
                html = html.replace("</head>", STYLE_TAG + "</head>");
            } else {
                html = STYLE_TAG + html;
            }
        }

        if (!html.contains("/js/accessibility.js")) {
            if (html.contains("</body>")) {
                html = html.replace("</body>", SCRIPT_TAG + "</body>");
            } else {
                html += SCRIPT_TAG;
            }
        }

        byte[] result = html.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(result.length);
        response.getOutputStream().write(result);
    }
}
