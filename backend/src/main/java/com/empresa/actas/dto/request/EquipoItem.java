package com.empresa.actas.dto.request;

import lombok.Data;

/**
 * DTO que representa un equipo de cómputo en una acta.
 *
 * Utilizado tanto en actas de entrega como de devolución.
 * Los campos se autocompletan desde GLPI al buscar por serial.
 * En devolución se requiere additionally el campo "estado".
 *
 * Mapeado a las variables del template Word con el prefijo "eq_N_" donde N
 * es el número de equipo (1-10).
 */
@Data
public class EquipoItem {

    private String serial = "";
    private String marca = "";
    private String tipo = "";
    private String modelo = "";
    private String inventario = "";
    private String estado = "";
    private String gb = "";
}
