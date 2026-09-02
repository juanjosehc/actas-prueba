package com.empresa.actas.controller;

import com.empresa.actas.dto.request.ActaRequest;
import com.empresa.actas.dto.response.ActaResponse;
import com.empresa.actas.dto.response.ErrorResponse;
import com.empresa.actas.service.ActaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controlador para la generación y descarga del acta de entrega.
 *
 * Endpoints:
 * - POST /generar-acta       → Genera acta + checklist, retorna nombre del ZIP.
 * - GET  /descargar-acta/{zip} → Descarga el ZIP generado.
 *
 * La generación delega completamente a ActaService.
 * La descarga sirve archivos desde el directorio configurado
 * en app.generated-dir con Content-Disposition: attachment.
 */
@RestController
public class ActaController {

    @Value("${app.generated-dir}")
    private String generatedDir;

    private final ActaService actaService;

    public ActaController(ActaService actaService) {
        this.actaService = actaService;
    }

    /**
     * Genera el acta de entrega y la lista de chequeo.
     *
     * @param request Datos del acta validados con @Valid.
     * @return ActaResponse con success y nombre_zip, o error.
     */
    @PostMapping("/generar-acta")
    public ActaResponse generarActa(@Valid @RequestBody ActaRequest request) {
        return actaService.generarActa(request);
    }

    /**
     * Descarga un archivo ZIP previamente generado.
     *
     * Si el archivo no existe retorna ErrorResponse con código 200
     * (el frontend espera 200 para todos los casos).
     *
     * @param nombreZip Nombre del archivo ZIP a descargar.
     * @return Archivo ZIP con Content-Type APPLICATION_OCTET_STREAM.
     */
    @GetMapping("/descargar-acta/{nombreZip}")
    public ResponseEntity<?> descargarActa(@PathVariable String nombreZip) {
        if (esNombreZipInvalido(nombreZip)) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("Nombre de archivo inválido"));
        }

        Path dir = Paths.get(generatedDir).toAbsolutePath().normalize();
        Path rutaZip = dir.resolve(nombreZip).normalize();

        // Defensa en profundidad contra path traversal
        // (ni siquiera debe intentarse servir algo fuera del directorio).
        if (!rutaZip.startsWith(dir) || !rutaZip.toFile().exists()) {
            return ResponseEntity.ok(ErrorResponse.of("Archivo no encontrado"));
        }

        Resource resource = new FileSystemResource(rutaZip.toFile());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreZip + "\"")
                .body(resource);
    }

    /**
     * Valida que el nombre del ZIP no intente salir del directorio
     * de archivos generados (rechaza separadores de ruta y "..").
     *
     * @param nombreZip Nombre del archivo a validar.
     * @return true si el nombre es inválido (traversal o malformado).
     */
    private boolean esNombreZipInvalido(String nombreZip) {
        return nombreZip == null
                || nombreZip.isBlank()
                || nombreZip.contains("..")
                || nombreZip.contains("/")
                || nombreZip.contains("\\");
    }
}
