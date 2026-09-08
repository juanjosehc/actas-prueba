package com.empresa.actas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Log de depuración CORS. Para cada petición cross-origin (trae header Origin)
 * y cada preflight OPTIONS, muestra:
 *  - el Origin recibido,
 *  - el resultado de CorsConfiguration.checkOrigin(origin) sobre la config
 *    aplicada (devuelve el origen si está permitido, null si NO),
 *  - el header Access-Control-Allow-Origin final de la respuesta.
 *
 * Se loguea DESPUÉS del doFilter: solo entonces el CorsFilter ya procesó la
 * petición y el header ACAO existe (o no). Loguear antes siempre daba null.
 */
@Component
public class CorsDebugFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorsDebugFilter.class);

    private final CorsConfigurationSource corsSource;

    public CorsDebugFilter(CorsConfigurationSource corsSource) {
        this.corsSource = corsSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (origin != null) {
                CorsConfiguration cfg = corsSource.getCorsConfiguration(request);
                String check = (cfg != null) ? cfg.checkOrigin(origin) : null;
                String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
                log.info("CORS [{}] {} origin={} checkOrigin={} allow-origin={} => {}",
                        request.getMethod(), request.getRequestURI(), origin, check, allowOrigin,
                        allowOrigin != null ? "PERMITIDO"
                                : (check != null ? "config OK pero sin ACAO en respuesta (ruta sin CORS?)"
                                                 : "ORIGEN NO PERMITIDO (revisar CORS_ALLOWED_ORIGINS)"));
            }
        }
    }
}
