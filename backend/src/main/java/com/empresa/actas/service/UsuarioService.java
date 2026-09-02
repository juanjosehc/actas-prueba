package com.empresa.actas.service;

import com.empresa.actas.dto.response.UsuarioResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servicio de búsqueda de usuarios en GLPI.
 *
 * Flujo de búsqueda:
 * 1. Autenticación con App-Token y User-Token (vía GlpiClient).
 * 2. Buscar en /search/User por firstname (field 9), realname (field 34)
 *    o login (field 1). Todos los criterios van con link OR: la API flat de
 *    GLPI no agrupa AND multi-término, así que se trae el conjunto OR y se
 *    rankea en Java por cuántos términos coincide cada usuario.
 * 3. Pedir explícitamente las columnas ID (2), login (1), firstname (9)
 *    y realname (34) con forcedisplay.
 * 4. Construir el nombre completo: firstname + " " + realname.
 * 5. Traer range=0-99, ordenar por cobertura de términos y devolver los 10 mejores.
 *
 * La autenticación y el HttpClient son compartidos a través de GlpiClient.
 *
 * Campos GLPI:
 * - Field 2:  ID del usuario.
 * - Field 1:  login (cuenta de usuario).
 * - Field 9:  firstname (nombres).
 * - Field 34: realname (apellidos).
 */
@Service
public class UsuarioService {

    private final GlpiClient glpiClient;

    public UsuarioService(GlpiClient glpiClient) {
        this.glpiClient = glpiClient;
    }

    /**
     * Busca usuarios en GLPI por términos, sobre firstname,
     * realname y login (campo name).
     *
     * Estrategia multi-término:
     * - El texto se separa en términos (split por espacios).
     * - Cada término debe aparecer en firstname, realname o login
     *   (criterios encadenados con OR dentro del grupo del término).
     * - Los grupos de términos se combinan con AND.
     *
     * Así "Julian Celis" encuentra un usuario cuyo firstname contenga
     * "Julian" y cuyo realname contenga "Celis", sin exigir palabras
     * consecutivas y en cualquier posición/campo.
     *
     * @param texto Texto de búsqueda (mínimo 3 caracteres).
     * @return Lista de usuarios con id, nombreCompleto y login.
     */
    public List<UsuarioResponse> buscarUsuarios(String texto) {
        List<UsuarioResponse> resultados = new ArrayList<>();

        if (texto == null || texto.trim().length() < 3) {
            return resultados;
        }

        String[] tokens = texto.trim().split("\\s+");
        if (tokens.length == 0) {
            return resultados;
        }

        try {
            // NOTA: todos los criterios van enlazados con OR (nunca AND).
            // La API flat de GLPI no agrupa "(term1 EN f9/f34/f1) AND (term2 EN ...)":
            // un AND entre grupos se ignora y devuelve usuarios que no cumplen todos
            // los términos. Por eso se trae el conjunto OR (más amplio) con range
            // amplio y se rankea por cobertura de términos en Java (ver ranking).
            // IMPORTANTE: el [link]=OR va en TODOS los criterios, incluido el último.
            // Si el último criterio no lleva link (p.ej. field=1, login), GLPI lo
            // combina con AND del grupo anterior y una búsqueda que solo matchea
            // en login devuelve 0 resultados.
            StringBuilder query = new StringBuilder("?");
            int p = 0;

            for (String token : tokens) {
                String valor = URLEncoder.encode(
                        token,
                        StandardCharsets.UTF_8
                );

                for (int f = 0; f < 3; f++) {
                    String campo = (f == 0) ? "9" : (f == 1) ? "34" : "1";
                    query.append("criteria[").append(p).append("][field]=").append(campo)
                            .append("&criteria[").append(p).append("][searchtype]=contains")
                            .append("&criteria[").append(p).append("][value]=").append(valor)
                            .append("&criteria[").append(p).append("][link]=OR");
                    query.append("&");
                    p++;
                }
            }

            query.append("forcedisplay[0]=2")
                    .append("&forcedisplay[1]=1")
                    .append("&forcedisplay[2]=9")
                    .append("&forcedisplay[3]=34")
                    .append("&range=0-99");

            JsonNode root = glpiClient.search("User", query.toString());
            JsonNode data = root.path("data");

            List<UsuarioResponse> encontrados = new ArrayList<>();
            List<Integer> puntajes = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode item : data) {
                    agregarConPuntaje(encontrados, puntajes, extraerId(item), item, tokens);
                }
            } else {
                Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    int id;
                    try {
                        id = Integer.parseInt(entry.getKey());
                    } catch (NumberFormatException e) {
                        id = extraerId(entry.getValue());
                    }
                    agregarConPuntaje(encontrados, puntajes, id, entry.getValue(), tokens);
                }
            }

            // Ranking: primero los que coinciden con más términos, luego se quedan 10.
            List<Integer> indice = new ArrayList<>();
            for (int i = 0; i < encontrados.size(); i++) {
                indice.add(i);
            }
            indice.sort((a, b) -> puntajes.get(b) - puntajes.get(a));

            int limite = Math.min(10, indice.size());
            for (int i = 0; i < limite; i++) {
                resultados.add(encontrados.get(indice.get(i)));
            }

        } catch (Exception e) {
            return resultados;
        }

        return resultados;
    }

    /**
     * Agrega un usuario a las listas junto con su puntaje de cobertura:
     * cuántos términos de la búsqueda aparecen en su nombre o login.
     */
    private void agregarConPuntaje(List<UsuarioResponse> encontrados,
                                   List<Integer> puntajes,
                                   int id,
                                   JsonNode item,
                                   String[] tokens) {
        if (id <= 0) {
            return;
        }

        String firstname = getFieldValue(item, "9");
        String realname = getFieldValue(item, "34");
        String login = getFieldValue(item, "1");

        String nombreCompleto = (firstname + " " + realname).trim();

        if (nombreCompleto.isEmpty() && login.isEmpty()) {
            return;
        }

        String full = (nombreCompleto + " " + login).toLowerCase(Locale.ROOT);
        int puntaje = 0;
        for (String token : tokens) {
            if (full.contains(token.toLowerCase(Locale.ROOT))) {
                puntaje++;
            }
        }

        encontrados.add(new UsuarioResponse(id, nombreCompleto, login));
        puntajes.add(puntaje);
    }

    /**
     * Extrae el id del usuario desde una fila del resultado de búsqueda.
     *
     * En el endpoint /search/User las columnas se identifican por el
     * número del searchoption. La columna "2" corresponde al ID del
     * usuario y se solicita explícitamente con forcedisplay[0]=2
     * (de lo contrario GLPI no la incluye en la respuesta).
     *
     * @param item Nodo JSON de la fila del resultado.
     * @return Id del usuario, o -1 si no se puede determinar.
     */
    private int extraerId(JsonNode item) {
        JsonNode idNode = item.path("id");
        if (idNode.canConvertToInt()) {
            return idNode.asInt(-1);
        }
        return item.path("2").asInt(-1);
    }

    /**
     * Extrae el valor de un campo específico de un nodo JSON de GLPI.
     *
     * Si el campo es array se concatena con espacio. Si es string
     * se retorna directamente.
     *
     * @param node    Nodo JSON del usuario.
     * @param fieldId ID del campo GLPI (como string).
     * @return Valor del campo, o cadena vacía si no existe.
     */
    private String getFieldValue(JsonNode node, String fieldId) {
        JsonNode valueNode = node.path(fieldId);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        if (valueNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : valueNode) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(item.asText(""));
            }
            return sb.toString().trim();
        }
        return valueNode.asText("");
    }
}
