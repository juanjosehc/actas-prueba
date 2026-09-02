package com.empresa.actas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para errores genéricos.
 *
 * Utilizado por el GlobalExceptionHandler y el endpoint
 * de descarga cuando el archivo no existe.
 * Contiene success (siempre false) y un mensaje descriptivo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private boolean success;
    private String mensaje;

    /**
     * Crea un ErrorResponse con el mensaje indicado.
     *
     * @param mensaje Descripción del error.
     * @return ErrorResponse con success=false.
     */
    public static ErrorResponse of(String mensaje) {
        return new ErrorResponse(false, mensaje);
    }
}
