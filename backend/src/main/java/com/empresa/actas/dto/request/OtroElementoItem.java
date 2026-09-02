package com.empresa.actas.dto.request;

import lombok.Data;

/**
 * DTO que representa un "otro elemento" en el acta de devolución.
 *
 * A diferencia de HardwareItem (entrega), solo captura el tipo.
 * Se mapea a las variables del template Word con el prefijo "ot_N_" donde N
 * es el número de elemento (1-10).
 */
@Data
public class OtroElementoItem {

    private String tipo = "";
}
