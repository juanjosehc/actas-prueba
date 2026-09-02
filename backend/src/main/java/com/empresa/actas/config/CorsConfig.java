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
 * - http://127.0.0.1       → Frontend servido localmente.
 * - http://localhost        → Variante localhost.
 * - http://127.0.0.1:5500  → Live Server de VS Code.
 * - http://localhost:5500   → Live Server de VS Code (variante).
 * - http://127.0.0.1:5501  → Live Server de VS Code (puerto actual del frontend).
 * - http://localhost:5501   → Live Server de VS Code (variante, puerto actual del frontend).
 * - http://127.0.0.1:8080   → Servidor alternativo.
 * - http://localhost:8080    → Servidor alternativo (variante).
 *
 * Métodos permitidos: todos (*).
 * Headers permitidos: todos (*).
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
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .exposedHeaders("Content-Disposition");
            }
        };
    }
}
