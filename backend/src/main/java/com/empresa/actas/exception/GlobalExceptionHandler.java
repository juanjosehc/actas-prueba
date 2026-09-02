package com.empresa.actas.exception;

import com.empresa.actas.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para la API REST.
 *
 * Intercepta excepciones lanzadas desde controllers y las convierte
 * en respuestas HTTP coherentes con formato ErrorResponse.
 *
 * Excepciones manejadas:
 * - MethodArgumentNotValidException → 400 Bad Request (validación @Valid).
 * - IllegalArgumentException      → 400 Bad Request (errores de negocio).
 * - Exception                      → 500 Internal Server Error (errores inesperados).
 *
 * Todas las respuestas usan el mismo formato JSON: { success, mensaje }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja errores de validación de campos (@Valid en DTOs).
     * Concatena todos los mensajes de error de campo con coma.
     *
     * @param ex Excepción con los errores de validación.
     * @return 400 Bad Request con ErrorResponse.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ErrorResponse.of(mensaje));
    }

    /**
     * Maneja errores de negocio (argumentos ilegales).
     *
     * @param ex Excepción con el mensaje de error.
     * @return 400 Bad Request con ErrorResponse.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ex.getMessage()));
    }

    /**
     * Maneja cualquier excepción no controlada.
     *
     * @param ex Excepción inesperada.
     * @return 500 Internal Server Error con ErrorResponse.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // El detalle técnico solo va al log del servidor,
        // nunca al cliente (evita filtrar rutas, trazas e internals).
        log.error("Error no controlado en la aplicación", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Error interno del servidor"));
    }
}
