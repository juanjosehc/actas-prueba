package com.empresa.actas.controller;

import com.empresa.actas.dto.request.FormateoSeguroRequest;
import com.empresa.actas.dto.response.ActaResponse;
import com.empresa.actas.service.FormateoSeguroService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para la generación del acta de formateo seguro.
 *
 * Endpoint:
 * - POST /generar-formateo-seguro → Genera acta de formateo seguro, retorna nombre del ZIP.
 *
 * Igual que DevolucionController: no tiene endpoint de descarga propio
 * porque reutiliza /descargar-acta/{zip}.
 */
@RestController
public class FormateoSeguroController {

    private final FormateoSeguroService formateoSeguroService;

    public FormateoSeguroController(FormateoSeguroService formateoSeguroService) {
        this.formateoSeguroService = formateoSeguroService;
    }

    /**
     * Genera el acta de formateo seguro.
     *
     * @param request Datos del acta validados con @Valid.
     * @return ActaResponse con success y nombre_zip, o error.
     */
    @PostMapping("/generar-formateo-seguro")
    public ActaResponse generarFormateoSeguro(@Valid @RequestBody FormateoSeguroRequest request) {
        return formateoSeguroService.generarFormateoSeguro(request);
    }
}