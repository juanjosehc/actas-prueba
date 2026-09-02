package com.empresa.actas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para la consulta de equipos desde GLPI.
 *
 * Contiene marca, tipo y modelo del equipo encontrado.
 * El frontend utiliza estos campos para auto completar
 * los datos del equipo al hacer click en "Buscar".
 *
 * Si no se encuentra el equipo, se retornan cadenas vacías.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipoResponse {

    private String marca = "";
    private String tipo = "";
    private String modelo = "";
}
