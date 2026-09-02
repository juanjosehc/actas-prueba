package com.empresa.actas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Spring Boot.
 *
 * La aplicación genera documentos Word (actas de entrega, checklist,
 * actas de devolución) empaquetados en ZIP para descarga desde el frontend.
 *
 * Puertos y configuración: application.yml.
 * CORS: CorsConfig.java.
 * Variables de entorno: AppConfig.java (carga desde .env).
 */
@SpringBootApplication
public class ActasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActasApplication.class, args);
    }
}
