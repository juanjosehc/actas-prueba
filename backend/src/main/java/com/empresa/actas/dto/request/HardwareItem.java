package com.empresa.actas.dto.request;

import lombok.Data;

/**
 * DTO que representa un elemento de hardware en el acta de entrega.
 *
 * Cada item contiene tres campos: tipo, descripción y programa.
 * Se mapea a las variables del template Word con el prefijo "hw_N_" donde N
 * es el número de hardware (1-11).
 */
@Data
public class HardwareItem {

    private String tipo = "";
    private String descripcion = "";
    private String programa = "";
}
