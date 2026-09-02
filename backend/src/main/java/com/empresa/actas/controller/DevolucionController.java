package com.empresa.actas.controller;

import com.empresa.actas.dto.request.DevolucionRequest;
import com.empresa.actas.dto.response.ActaResponse;
import com.empresa.actas.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para la generación del acta de devolución.
 *
 * Endpoint:
 * - POST /generar-devolucion → Genera acta de devolución, retorna nombre del ZIP.
 *
 * A diferencia de ActaController, no tiene endpoint de descarga
 * porque reutiliza el mismo endpoint /descargar-acta/{zip}.
 * La generación delega completamente a DevolucionService.
 */
@RestController
public class DevolucionController {

    private final DevolucionService devolucionService;

    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    /**
     * Genera el acta de devolución.
     *
     * @param request Datos del acta validados con @Valid.
     * @return ActaResponse con success y nombre_zip, o error.
     */
    @PostMapping("/generar-devolucion")
    public ActaResponse generarDevolucion(@Valid @RequestBody DevolucionRequest request) {
        return devolucionService.generarDevolucion(request);
    }
}
