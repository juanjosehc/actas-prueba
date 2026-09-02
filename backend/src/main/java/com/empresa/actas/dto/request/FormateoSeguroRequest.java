package com.empresa.actas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de entrada para la generación del acta de formateo seguro.
 *
 * Contiene la información necesaria para generar el memorando de
 * formateo seguro (DOCX).
 *
 * Diferencias con ActaRequest y DevolucionRequest:
 * - No incluye checklist, sistema operativo, hardware ni estado.
 * - Incluye asunto.
 * - Máximo 4 equipos (capacidad de la plantilla DOCX).
 * - Cada equipo incluye campo "gb" (cantidad en gigas).
 */
@Data
public class FormateoSeguroRequest {

    @NotBlank(message = "La fecha es obligatoria")
    private String fecha;

    @NotBlank(message = "El campo entregado a es obligatorio")
    private String entregado_a = "";

    @NotBlank(message = "El cargo de quien recibe es obligatorio")
    private String cargo_recibe = "";

    @NotBlank(message = "El campo entregado por es obligatorio")
    private String entregado_por = "";

    @NotBlank(message = "El cargo de quien entrega es obligatorio")
    private String cargo_entrega = "";

    @NotBlank(message = "El asunto es obligatorio")
    private String asunto = "";

    @Size(max = 4, message = "Máximo 4 equipos (capacidad de la plantilla)")
    private List<EquipoItem> equipos = new ArrayList<>();
}