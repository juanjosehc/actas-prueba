package com.empresa.actas.service;

import com.empresa.actas.dto.request.ActaRequest;
import com.empresa.actas.dto.response.ActaResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Servicio orquestador para la generación del acta de entrega.
 *
 * Flujo:
 * 1. Crear directorio de salida si no existe.
 * 2. Convertir ActaRequest a Map<String, Object> para el motor de templates.
 * 3. Generar acta de entrega (DOCX) vía DocumentoWordService.
 * 4. Generar lista de chequeo (DOCX) vía DocumentoWordService.
 * 5. Empaquetar ambos DOCX en un ZIP vía ZipService.
 * 6. Retornar ActaResponse con el nombre del ZIP.
 *
 * Naming del ZIP: ActaEntrega_{SERIAL}_{NOMBRE}.zip
 */
@Service
public class ActaService {

    private static final Logger log =
            LoggerFactory.getLogger(ActaService.class);

    @Value("${app.generated-dir}")
    private String generatedDir;

    private final DocumentoWordService wordService;
    private final ZipService zipService;
    private final ObjectMapper objectMapper;

    public ActaService(
            DocumentoWordService wordService,
            ZipService zipService,
            ObjectMapper objectMapper
    ) {
        this.wordService = wordService;
        this.zipService = zipService;
        this.objectMapper = objectMapper;
    }

    /**
     * Genera el acta de entrega completa (acta + checklist) empaquetada en ZIP.
     *
     * @param request Datos del acta validados previamente por el controller.
     * @return ActaResponse con success=true y nombre_zip, o success=false con error.
     */
    public ActaResponse generarActa(ActaRequest request) {
        try {
            Path outputDir = Paths.get(generatedDir);
            Files.createDirectories(outputDir);

            Map<String, Object> datos = objectMapper.convertValue(
                    request,
                    new TypeReference<Map<String, Object>>() {}
            );

            Path rutaActa = wordService.generarActa(datos);

            Path rutaChecklist = wordService.generarChecklist(datos);

            String serial = "SinSerial";
            if (request.getEquipos() != null && !request.getEquipos().isEmpty()) {
                serial = NombreArchivoUtil.normalizarSerial(
                        request.getEquipos().get(0).getSerial());
            }

            // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
            String base = NombreArchivoUtil.nombreBase(
                    "ActaEntrega", serial, request.getEntregado_a());
            String nombreZip = base + ".zip";
            Path rutaZip = outputDir.resolve(nombreZip);

            zipService.crearZip(rutaZip, rutaActa, rutaChecklist);

            return ActaResponse.ok(nombreZip);

        } catch (Exception e) {
            log.error("Error generando acta de entrega", e);
            return ActaResponse.error("Error generando documentación");
        }
    }
}
