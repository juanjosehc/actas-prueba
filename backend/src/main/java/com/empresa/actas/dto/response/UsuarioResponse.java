package com.empresa.actas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para la búsqueda de usuarios en GLPI.
 *
 * Contiene los datos necesarios para el autocompletado en
 * el frontend: id, nombre completo CONCAT(firstname, ' ',
 * realname) y login (campo name de GLPI).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private int id;
    private String nombreCompleto;
    private String login;
}
