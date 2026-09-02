package com.empresa.actas.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de empaquetamiento de archivos en ZIP.
 *
 * Utilizado por ActaService, DevolucionService y FormateoSeguroService
 * para comprimir los DOCX generados en un solo archivo para descarga.
 *
 * Soporta un número variable de archivos (varargs).
 * El ZIP se escribe en el Path de destino especificado.
 */
@Service
public class ZipService {

    /**
     * Crea un archivo ZIP con los archivos indicados.
     *
     * @param destino Ruta del archivo ZIP de salida.
     * @param archivos Archivos a incluir en el ZIP.
     * @throws IOException Si hay error al escribir el ZIP.
     */
    public void crearZip(Path destino, Path... archivos) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new java.io.FileOutputStream(destino.toFile()))) {

            for (Path archivo : archivos) {
                ZipEntry entry = new ZipEntry(archivo.getFileName().toString());
                zos.putNextEntry(entry);
                java.nio.file.Files.copy(archivo, zos);
                zos.closeEntry();
            }
        }
    }
}
