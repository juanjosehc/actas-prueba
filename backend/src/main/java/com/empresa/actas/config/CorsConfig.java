package com.empresa.actas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de CORS para permitir peticiones desde el frontend.
 *
 * Orígenes permitidos:
 * - local (Live Server, etc.) y producción interna.
 * - https://*.vercel.app  → frontend estático desplegado en Vercel
 *   (wildcard cubre dominio de proyecto y previews; no requiere conocer
 *   el subdominio exacto). Se puede restringir vía CORS_ALLOWED_ORIGINS.
 *
 * Métodos permitidos: todos (*), incluye GET, POST y OPTIONS (preflight).
 * Headers permitidos: todos (*), incluye Authorization (Bearer token).
 * Headers expuestos: Content-Disposition (necesario para descarga de ZIP).
 */
@Configuration
public class CorsConfig {

    /**
     * Lista de orígenes permitidos (separados por coma).
     * Configurable en producción vía CORS_ALLOWED_ORIGINS.
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // allowedOriginPatterns (no allowedOrigins): admite
                        // wildcards como https://*.vercel.app.
                        .allowedOriginPatterns(allowedOrigins.split(","))
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Content-Disposition");
            }
        };
    }
}
