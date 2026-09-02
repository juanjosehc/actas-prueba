package com.empresa.actas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP compartido para la API REST de GLPI.
 *
 * Centraliza la autenticación (App-Token + User-Token) y el
 * HttpClient usado por los servicios que consultan GLPI
 * (equipos, usuarios). Evita integraciones paralelas
 * duplicadas en cada servicio.
 *
 * Flujo:
 * 1. iniciarSesion() → obtiene el Session-Token.
 * 2. search(itemtype, query) → ejecuta una búsqueda autenticada
 *    sobre /search/{itemtype} y retorna el JSON parseado.
 *
 * Configuración (application.yml):
 * - glpi.url
 * - glpi.app-token
 * - glpi.user-token
 */
@Component
public class GlpiClient {

    @Value("${glpi.url}")
    private String glpiUrl;

    @Value("${glpi.app-token}")
    private String appToken;

    @Value("${glpi.user-token}")
    private String userToken;

    // Timeouts para no dejar requests colgados si GLPI está caído o lento.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Inicia sesión en la API de GLPI y retorna el session token.
     *
     * @return Session token para las siguientes peticiones.
     * @throws Exception Si hay error de conexión o autenticación.
     */
    public String iniciarSesion() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(glpiUrl + "/initSession"))
                .timeout(REQUEST_TIMEOUT)
                .header("App-Token", appToken)
                .header("Authorization", "user_token " + userToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("session_token").asText();
    }

    /**
     * Ejecuta una búsqueda autenticada en la API de GLPI.
     *
     * @param itemtype Tipo de item GLPI (ej: "Computer", "User").
     * @param query    Query string a partir de "?" (criteria, forcedisplay, range...).
     * @return JSON raíz de la respuesta de GLPI (contiene count y data).
     * @throws Exception Si la sesión falla, la respuesta no es 2xx
     *                   o el cuerpo no es JSON válido.
     */
    public JsonNode search(String itemtype, String query) throws Exception {
        String sessionToken = iniciarSesion();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(glpiUrl + "/search/" + itemtype + query))
                .timeout(REQUEST_TIMEOUT)
                .header("App-Token", appToken)
                .header("Session-Token", sessionToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                    "GLPI respondió HTTP " + status
            );
        }

        return objectMapper.readTree(response.body());
    }
}
