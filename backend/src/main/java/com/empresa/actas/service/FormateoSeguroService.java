package com.empresa.actas.service;

import com.empresa.actas.dto.request.FormateoSeguroRequest;
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
 * Servicio orquestador para la generación del acta de formateo seguro.
 *
 * Flujo:
 * 1. Crear directorio de salida si no existe.
 * 2. Convertir FormateoSeguroRequest a Map<String, Object> para el motor de templates.
 * 3. Generar acta de formateo seguro (DOCX) vía DocumentoWordService.
 * 4. Empaquetar el DOCX en un ZIP vía ZipService.
 * 5. Retornar ActaResponse con el nombre del ZIP.
 *
 * Mismo patrón que DevolucionService: solo un DOCX (sin checklist).
 * Naming del ZIP: ActaFormateoSeguro_{SERIAL}_{NOMBRE}.zip
 */
@Service
public class FormateoSeguroService {

    private static final Logger log =
            LoggerFactory.getLogger(FormateoSeguroService.class);

    @Value("${app.generated-dir}")
    private String generatedDir;

    private final DocumentoWordService wordService;
    private final ZipService zipService;
    private final ObjectMapper objectMapper;

    public FormateoSeguroService(
            DocumentoWordService wordService,
            ZipService zipService,
            ObjectMapper objectMapper
    ) {
        this.wordService = wordService;
        this.zipService = zipService;
        this.objectMapper = objectMapper;
    }

    /**
     * Genera el acta de formateo seguro empaquetada en ZIP.
     *
     * @param request Datos del acta validados previamente por el controller.
     * @return ActaResponse con success=true y nombre_zip, o success=false con error.
     */
    public ActaResponse generarFormateoSeguro(FormateoSeguroRequest request) {
        try {
            Path outputDir = Paths.get(generatedDir);
            Files.createDirectories(outputDir);

            Map<String, Object> datos = objectMapper.convertValue(
                    request,
                    new TypeReference<Map<String, Object>>() {}
            );

            Path rutaActa = wordService.generarFormateoSeguro(datos);

            String serial = "SinSerial";
            if (request.getEquipos() != null && !request.getEquipos().isEmpty()) {
                serial = NombreArchivoUtil.normalizarSerial(
                        request.getEquipos().get(0).getSerial());
            }

            // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
            String base = NombreArchivoUtil.nombreBase(
                    "ActaFormateoSeguro", serial, request.getEntregado_por());
            String nombreZip = base + ".zip";
            Path rutaZip = outputDir.resolve(nombreZip);

            zipService.crearZip(rutaZip, rutaActa);

            return ActaResponse.ok(nombreZip);

        } catch (Exception e) {
            log.error("Error generando acta de formateo seguro", e);
            return ActaResponse.error("Error generando acta de formateo seguro");
        }
    }
}