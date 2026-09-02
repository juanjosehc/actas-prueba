package com.empresa.actas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Log de depuración CORS. Loguea cada petición cross-origin (trae header Origin)
 * y cada preflight OPTIONS, con el método, la URI y el Origin que el navegador envió.
 *
 * Útil para diagnosticar "CORS blocked": si el Origin del log no está en la lista
 * permitida (app.cors.allowed-origins / CORS_ALLOWED_ORIGINS), ahí está la causa.
 */
@Component
public class CorsDebugFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorsDebugFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        if (origin != null) {
            log.info("CORS request: [{}] {} origen={} -> allow-origin={}",
                    request.getMethod(), request.getRequestURI(), origin,
                    response.getHeader("Access-Control-Allow-Origin"));
        }

        filterChain.doFilter(request, response);
    }
}