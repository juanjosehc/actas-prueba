package com.empresa.actas.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta exitosa para operaciones de generación de actas.
 *
 * Utilizado tanto para actas de entrega como de devolución.
 * Contiene el nombre del ZIP generado para que el frontend
 * pueda solicitarlo vía /descargar-acta/{nombreZip}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActaResponse {

    private boolean success;

    @JsonProperty("nombre_zip")
    private String nombreZip;

    private String mensaje;

    /**
     * Crea una respuesta exitosa con el nombre del ZIP generado.
     *
     * @param nombreZip Nombre del archivo ZIP para descarga.
     * @return ActaResponse con success=true.
     */
    public static ActaResponse ok(String nombreZip) {
        return new ActaResponse(true, nombreZip, "Documentación generada correctamente");
    }

    /**
     * Crea una respuesta de error con un mensaje descriptivo.
     *
     * @param mensaje Descripción del error.
     * @return ActaResponse con success=false.
     */
    public static ActaResponse error(String mensaje) {
        return new ActaResponse(false, null, mensaje);
    }
}
