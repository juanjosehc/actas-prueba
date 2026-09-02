package com.empresa.actas.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Configuración general de la aplicación.
 *
 * Responsabilidades:
 * - Cargar variables de entorno desde el archivo .env (ubicado en la raíz del proyecto).
 * - Crear el directorio de archivos generados si no existe.
 *
 * El archivo .env contiene:
 * - GLPI_URL: URL base de la instancia GLPI.
 * - GLPI_APP_TOKEN: Token de aplicación para la API de GLPI.
 * - GLPI_USER_TOKEN: Token de usuario para la API de GLPI.
 *
 * Las variables se cargan como System properties (no como env vars)
 * para que Spring pueda inyectarlas con @Value.
 */
@Configuration
public class AppConfig {

    @Value("${app.generated-dir}")
    private String generatedDir;

    /**
     * Inicialización post-construcción de Spring.
     * Carga el .env y crea el directorio de salida.
     */
    @PostConstruct
    public void init() {
        loadDotenv();

        File dir = new File(generatedDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Carga variables desde .env y las establece como System properties.
     * Solo establece la variable si no existe previamente en el entorno.
     * Esto permite que las variables de sistema (Docker, CI/CD) tengan prioridad.
     */
    private void loadDotenv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("../")
                    .filename(".env")
                    .load();

            setEnvIfMissing("GLPI_URL", dotenv.get("GLPI_URL"));
            setEnvIfMissing("GLPI_APP_TOKEN", dotenv.get("GLPI_APP_TOKEN"));
            setEnvIfMissing("GLPI_USER_TOKEN", dotenv.get("GLPI_USER_TOKEN"));
        } catch (Exception e) {
            System.out.println("No se pudo cargar .env: " + e.getMessage());
        }
    }

    /**
     * Establece una System property solo si no existe en el entorno actual.
     *
     * @param key   Nombre de la variable.
     * @param value Valor a establecer.
     */
    private void setEnvIfMissing(String key, String value) {
        if (value != null && System.getenv(key) == null) {
            System.setProperty(key, value);
        }
    }
}
