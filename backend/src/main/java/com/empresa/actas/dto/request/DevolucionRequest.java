package com.empresa.actas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de entrada para la generación del acta de devolución.
 *
 * Contiene la información necesaria para generar el acta de devolución (DOCX).
 *
 * Diferencias con ActaRequest:
 * - No incluye checklist ni sistema operativo.
 * - No incluye hardware detallado (solo tipo).
 * - Incluye campo cedula del entregador.
 *
 * Solo fecha es obligatoria con @NotBlank; los demás campos
 * se validan en el frontend antes de enviar.
 */
@Data
public class DevolucionRequest {

    @NotBlank(message = "La fecha es obligatoria")
    private String fecha;

    private String recibido_por = "";

    private String entregado_por = "";

    private String cargo_recibe = "";

    private String cedula = "";

    private String area_recibe = "";

    private String motivo = "";

    private String cargo_entrega = "";

    private List<EquipoItem> equipos = new ArrayList<>();

    private List<OtroElementoItem> hardware = new ArrayList<>();

    private String observaciones = "";
}
