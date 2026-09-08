package com.empresa.actas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Cliente HTTP compartido para la API REST de GLPI.
 *
 * Centraliza la autenticación (App-Token + User-Token) y el
 * cliente HTTP usado por los servicios que consultan GLPI
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
 *
 * TLS: el host GLPI (sac-i.connser.com.co) sirve un wildcard
 * (CN=*.coltefinanciera.com.co) que no lo cubre. Hay que relajar
 * validación de certificado y hostname SOLO para esta conexión.
 * Reemplazar por fix de SAN en el certificado cuando IT lo corrija.
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
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final int REQUEST_TIMEOUT_MS = (int) Duration.ofSeconds(30).toMillis();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ponytail/security: desactiva validación TLS SOLO para esta conexión,
     * porque el host sac-i.connser.com.co sirve un wildcard que no lo cubre
     * (CN=*.coltefinanciera.com.co) y Java lo rechaza. Reemplazar por fix de
     * SAN en el certificado cuando IT lo corrija. No toca otros clientes.
     */
    private final SSLContext trustAllSslContext = trustAllSslContext();
    private final HostnameVerifier trustAllHostnameVerifier = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear SSLContext trust-all para GLPI", e);
        }
    }

    /**
     * Inicia sesión en la API de GLPI y retorna el session token.
     *
     * @return Session token para las siguientes peticiones.
     * @throws Exception Si hay error de conexión o autenticación.
     */
    public String iniciarSesion() throws Exception {
        String body = ejecutarGet(glpiUrl + "/initSession", "App-Token", appToken,
                "Authorization", "user_token " + userToken);
        JsonNode root = objectMapper.readTree(body);
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
        String body = ejecutarGet(glpiUrl + "/search/" + itemtype + query,
                "App-Token", appToken,
                "Session-Token", sessionToken);
        return objectMapper.readTree(body);
    }

    /**
     * GET con headers, devolviendo el cuerpo como string.
     * Aplica los timeouts, el SSLContext trust-all y el hostnameVerifier
     * por conexión (solo este cliente).
     */
    private String ejecutarGet(String urlString, String... headers) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(REQUEST_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            // Aplicar headers pares (clave, valor).
            for (int i = 0; i < headers.length; i += 2) {
                conn.setRequestProperty(headers[i], headers[i + 1]);
            }

            if (conn instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
                https.setHostnameVerifier(trustAllHostnameVerifier);
            }

            int status = conn.getResponseCode();

            InputStream input = (status < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            String body = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            if (status < 200 || status >= 300) {
                throw new RuntimeException(
                        "GLPI respondió HTTP " + status + ": " + body
                );
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }
}