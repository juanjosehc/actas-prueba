package com.empresa.actas.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Configuración de CORS.
 *
 * Se usa un {@link CorsFilter} (filtro servlet de máxima precedencia) en lugar
 * de WebMvcConfigurer: así el header Access-Control-Allow-Origin se aplica a
 * TODAS las respuestas (preflight OPTIONS, endpoints, errores/404), no solo a
 * rutas con controller mapeado.
 *
 * Orígenes permitidos (property app.cors.allowed-origins / CORS_ALLOWED_ORIGINS):
 * - local (Live Server, etc.) y producción interna.
 * - https://*.vercel.app  → wildcard cubre dominio de proyecto y previews.
 *
 * Métodos permitidos: GET, POST y OPTIONS (preflight).
 * Headers permitidos: todos (*), incluye Authorization (Bearer token).
 * Headers expuestos: Content-Disposition (descarga de ZIP).
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * Lista de orígenes permitidos (separados por coma).
     * Configurable en producción vía CORS_ALLOWED_ORIGINS.
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // trim por elemento: un espacio tras la coma en CORS_ALLOWED_ORIGINS
        // rompería el match del patrón/origen.
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        log.info("CORS allowed-origins configurados: {}", Arrays.toString(origins));

        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPatterns (no allowedOrigins): admite wildcards tipo https://*.vercel.app.
        config.setAllowedOriginPatterns(Arrays.asList(origins));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Máxima precedencia: corre antes que cualquier otro filtro, así el
     * Origin de toda petición se procesa (o rechaza) aquí.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter(CorsConfigurationSource source) {
        return new CorsFilter(source);
    }
}
