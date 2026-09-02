package com.empresa.actas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de generación de documentos Word (DOCX).
 *
 * Responsabilidades:
 * - Preparar los datos del request para los templates.
 * - Generar acta de entrega, checklist y acta de devolución.
 * - Delegar el reemplazo de placeholders a DocxTemplateEngine.
 *
 * Cada método:
 * 1. Prepara datos (fecha descompuesta, hardware/equipos indexados).
 * 2. Convierte todo a Map<String, String> para el template engine.
 * 3. Resuelve la ruta del template.
 * 4. Genera el nombre del archivo de salida.
 * 5. Procesa el template y retorna la ruta del DOCX generado.
 *
 * Límites por template:
 * - Acta entrega: 11 hardware items, 10 equipos.
 * - Checklist: 36 checkboxes, 1 equipo (primero).
 * - Devolución: 10 equipos con estado, 10 otros elementos.
 */
@Service
public class DocumentoWordService {

    @Value("${app.generated-dir}")
    private String generatedDir;

    @Value("${app.templates-dir}")
    private String templatesDir;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Genera el acta de entrega (DOCX).
     *
     * Prepara:
     * - Fecha descompuesta en dia, mes, anio.
     * - 11 items de hardware indexados (hw_N_tipo, hw_N_descripcion, hw_N_programa).
     * - 10 equipos indexados (eq_N_marca, eq_N_tipo, eq_N_modelo, eq_N_serial, eq_N_inventario).
     *
     * Template: "Acta de Entrega 2 2 - copia.docx"
     * Salida: ActaEntrega_{SERIAL}_{NOMBRE}.docx
     *
     * @param datos Mapa de datos con el contenido del acta.
     * @return Ruta del DOCX generado.
     * @throws IOException Si hay error de E/S al leer template o escribir salida.
     */
    public Path generarActa(Map<String, Object> datos) throws IOException {

        Path outputDir = Paths.get(generatedDir);
        Files.createDirectories(outputDir);

        prepararFecha(datos);

        for (int i = 1; i <= 16; i++) {
            datos.put("hw_" + i + "_tipo", "");
            datos.put("hw_" + i + "_descripcion", "");
            datos.put("hw_" + i + "_programa", "");
        }

        List<Map<String, Object>> hwList = asMapList(datos.get("hardware"));
        int idx = 0;
        for (Map<String, Object> hw : hwList) {
            idx++;
            if (idx > 11) break;
            datos.put("hw_" + idx + "_tipo", hw.getOrDefault("tipo", ""));
            datos.put("hw_" + idx + "_descripcion", hw.getOrDefault("descripcion", ""));
            datos.put("hw_" + idx + "_programa", hw.getOrDefault("programa", ""));
        }

        for (int i = 1; i <= 10; i++) {
            datos.put("eq_" + i + "_marca", "");
            datos.put("eq_" + i + "_tipo", "");
            datos.put("eq_" + i + "_modelo", "");
            datos.put("eq_" + i + "_serial", "");
            datos.put("eq_" + i + "_inventario", "");
        }

        List<Map<String, Object>> eqList = asMapList(datos.get("equipos"));
        idx = 0;
        for (Map<String, Object> eq : eqList) {
            idx++;
            if (idx > 10) break;
            datos.put("eq_" + idx + "_marca", eq.getOrDefault("marca", ""));
            datos.put("eq_" + idx + "_tipo", eq.getOrDefault("tipo", ""));
            datos.put("eq_" + idx + "_modelo", eq.getOrDefault("modelo", ""));
            datos.put("eq_" + idx + "_serial",
                    NombreArchivoUtil.normalizarSerial(String.valueOf(eq.getOrDefault("serial", ""))));
            datos.put("eq_" + idx + "_inventario", eq.getOrDefault("inventario", ""));
        }

        Map<String, String> vars = new HashMap<>();
        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            vars.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        Path templatePath = resolveTemplate("Acta de Entrega 2 2 - copia.docx");

        String serial = "SinSerial";
        if (!eqList.isEmpty()) {
            serial = NombreArchivoUtil.normalizarSerial(
                    eqList.get(0).getOrDefault("serial", "SinSerial").toString());
        }

        // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
        String base = NombreArchivoUtil.nombreBase(
                "ActaEntrega", serial, datos.get("entregado_a"));
        Path outputPath = outputDir.resolve(base + ".docx");

        return DocxTemplateEngine.processTemplate(templatePath, vars, outputPath);
    }

    /**
     * Genera la lista de chequeo (DOCX).
     *
     * Prepara:
     * - Fecha descompuesta en dia, mes, anio.
     * - responsable_verificacion = entregado_por.
     * - Sistema operativo como checkboxes (win10, win11, macos) con X/vacío.
     * - 36 checkboxes (chk_N_si / chk_N_no) con X/vacío.
     * - Solo el primer equipo (para la sección de identificación).
     *
     * Template: "ListaChequeo.docx"
     * Salida: Checklist_{SERIAL}_{NOMBRE}.docx
     *
     * @param datos Mapa de datos con el contenido del checklist.
     * @return Ruta del DOCX generado.
     * @throws IOException Si hay error de E/S al leer template o escribir salida.
     */
    public Path generarChecklist(Map<String, Object> datos) throws IOException {

        Path outputDir = Paths.get(generatedDir);
        Files.createDirectories(outputDir);

        prepararFecha(datos);

        datos.put("responsable_verificacion",
                datos.getOrDefault("entregado_por", ""));

        String so = datos.getOrDefault("sistema_operativo", "").toString();

        datos.put("win10", "Windows 10".equals(so) ? "X" : "");
        datos.put("win11", "Windows 11".equals(so) ? "X" : "");
        datos.put("macos", "Mac OS".equals(so) ? "X" : "");

        Map<String, Object> chk = asMap(datos.get("checklist"));
        for (int i = 1; i <= 36; i++) {
            boolean valor = false;
            Object val = chk.get("chk_" + i);
            if (val instanceof Boolean b) {
                valor = b;
            } else if (val != null) {
                valor = Boolean.parseBoolean(val.toString());
            }
            datos.put("chk_" + i + "_si", valor ? "X" : "");
            datos.put("chk_" + i + "_no", valor ? "" : "X");
        }

        List<Map<String, Object>> eqList = asMapList(datos.get("equipos"));
        if (!eqList.isEmpty()) {
            Map<String, Object> eq = eqList.get(0);
            datos.put("eq_1_marca", eq.getOrDefault("marca", ""));
            datos.put("eq_1_tipo", eq.getOrDefault("tipo", ""));
            datos.put("eq_1_modelo", eq.getOrDefault("modelo", ""));
            datos.put("eq_1_serial",
                    NombreArchivoUtil.normalizarSerial(String.valueOf(eq.getOrDefault("serial", ""))));
            datos.put("eq_1_inventario", eq.getOrDefault("inventario", ""));
        } else {
            datos.put("eq_1_marca", "");
            datos.put("eq_1_tipo", "");
            datos.put("eq_1_modelo", "");
            datos.put("eq_1_serial", "");
            datos.put("eq_1_inventario", "");
        }

        Map<String, String> vars = new HashMap<>();
        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            vars.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        Path templatePath = resolveTemplate("ListaChequeo.docx");

        String serial = "SinSerial";
        if (!eqList.isEmpty()) {
            serial = NombreArchivoUtil.normalizarSerial(
                    eqList.get(0).getOrDefault("serial", "SinSerial").toString());
        }

        // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
        String base = NombreArchivoUtil.nombreBase(
                "Checklist", serial, datos.get("entregado_a"));
        Path outputPath = outputDir.resolve(base + ".docx");

        return DocxTemplateEngine.processTemplate(templatePath, vars, outputPath);
    }

    /**
     * Genera el acta de devolución (DOCX).
     *
     * Prepara:
     * - Fecha descompuesta en dia, mes, anio.
     * - 10 equipos indexados con campo estado adicional.
     * - 10 "otros elementos" indexados (ot_N_tipo).
     *
     * Template: "ActaDevolucion.docx"
     * Salida: ActaDevolucion_{SERIAL}_{NOMBRE}.docx
     *
     * @param datos Mapa de datos con el contenido del acta de devolución.
     * @return Ruta del DOCX generado.
     * @throws IOException Si hay error de E/S al leer template o escribir salida.
     */
    public Path generarDevolucion(Map<String, Object> datos) throws IOException {

        Path outputDir = Paths.get(generatedDir);
        Files.createDirectories(outputDir);

        prepararFecha(datos);

        for (int i = 1; i <= 10; i++) {
            datos.put("eq_" + i + "_marca", "");
            datos.put("eq_" + i + "_tipo", "");
            datos.put("eq_" + i + "_modelo", "");
            datos.put("eq_" + i + "_serial", "");
            datos.put("eq_" + i + "_inventario", "");
            datos.put("eq_" + i + "_estado", "");
        }

        List<Map<String, Object>> eqList = asMapList(datos.get("equipos"));
        int idx = 0;
        for (Map<String, Object> eq : eqList) {
            idx++;
            if (idx > 10) break;
            datos.put("eq_" + idx + "_marca", eq.getOrDefault("marca", ""));
            datos.put("eq_" + idx + "_tipo", eq.getOrDefault("tipo", ""));
            datos.put("eq_" + idx + "_modelo", eq.getOrDefault("modelo", ""));
            datos.put("eq_" + idx + "_serial",
                    NombreArchivoUtil.normalizarSerial(String.valueOf(eq.getOrDefault("serial", ""))));
            datos.put("eq_" + idx + "_inventario", eq.getOrDefault("inventario", ""));
            datos.put("eq_" + idx + "_estado", eq.getOrDefault("estado", ""));
        }

        for (int i = 1; i <= 10; i++) {
            datos.put("ot_" + i + "_tipo", "");
        }

        List<Map<String, Object>> hwList = asMapList(datos.get("hardware"));
        idx = 0;
        for (Map<String, Object> hw : hwList) {
            idx++;
            if (idx > 10) break;
            datos.put("ot_" + idx + "_tipo", hw.getOrDefault("tipo", ""));
        }

        Map<String, String> vars = new HashMap<>();
        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            vars.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        Path templatePath = resolveTemplate("ActaDevolucion.docx");

        String serial = "SinSerial";
        if (!eqList.isEmpty()) {
            serial = NombreArchivoUtil.normalizarSerial(
                    eqList.get(0).getOrDefault("serial", "SinSerial").toString());
        }

        // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
        String base = NombreArchivoUtil.nombreBase(
                "ActaDevolucion", serial, datos.get("entregado_por"));
        Path outputPath = outputDir.resolve(base + ".docx");

        return DocxTemplateEngine.processTemplate(templatePath, vars, outputPath);
    }

    /**
     * Genera el acta de formateo seguro (DOCX).
     *
     * Prepara:
     * - Fecha descompuesta en dia, mes, anio.
     * - Alias entrega_por = entregado_por (la firma del template usa
     *   {{entrega_por}}; no se modifica la plantilla).
     * - 4 equipos indexados (eq_N_marca, eq_N_tipo, eq_N_modelo,
     *   eq_N_serial, eq_N_inventario, eq_N_gb).
     *
     * Template: "ActaFormateoSeguro.docx"
     * Salida: ActaFormateoSeguro_{SERIAL}_{NOMBRE}.docx
     *
     * @param datos Mapa de datos con el contenido del acta.
     * @return Ruta del DOCX generado.
     * @throws IOException Si hay error de E/S al leer template o escribir salida.
     */
    public Path generarFormateoSeguro(Map<String, Object> datos) throws IOException {

        Path outputDir = Paths.get(generatedDir);
        Files.createDirectories(outputDir);

        prepararFecha(datos);

        datos.put("entrega_por",
                datos.getOrDefault("entregado_por", ""));

        for (int i = 1; i <= 4; i++) {
            datos.put("eq_" + i + "_marca", "");
            datos.put("eq_" + i + "_tipo", "");
            datos.put("eq_" + i + "_modelo", "");
            datos.put("eq_" + i + "_serial", "");
            datos.put("eq_" + i + "_inventario", "");
            datos.put("eq_" + i + "_gb", "");
        }

        List<Map<String, Object>> eqList = asMapList(datos.get("equipos"));
        int idx = 0;
        for (Map<String, Object> eq : eqList) {
            idx++;
            if (idx > 4) break;
            datos.put("eq_" + idx + "_marca", eq.getOrDefault("marca", ""));
            datos.put("eq_" + idx + "_tipo", eq.getOrDefault("tipo", ""));
            datos.put("eq_" + idx + "_modelo", eq.getOrDefault("modelo", ""));
            datos.put("eq_" + idx + "_serial",
                    NombreArchivoUtil.normalizarSerial(String.valueOf(eq.getOrDefault("serial", ""))));
            datos.put("eq_" + idx + "_inventario", eq.getOrDefault("inventario", ""));
            datos.put("eq_" + idx + "_gb", eq.getOrDefault("gb", ""));
        }

        Map<String, String> vars = new HashMap<>();
        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            vars.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        Path templatePath = resolveTemplate("ActaFormateoSeguro.docx");

        String serial = "SinSerial";
        if (!eqList.isEmpty()) {
            serial = NombreArchivoUtil.normalizarSerial(
                    eqList.get(0).getOrDefault("serial", "SinSerial").toString());
        }

        // Naming centralizado: [TIPO_ACTA]_[SERIAL]_[NOMBRE] vía NombreArchivoUtil.
        String base = NombreArchivoUtil.nombreBase(
                "ActaFormateoSeguro", serial, datos.get("entregado_por"));
        Path outputPath = outputDir.resolve(base + ".docx");

        return DocxTemplateEngine.processTemplate(templatePath, vars, outputPath);
    }

    /**
     * Resuelve la ruta de un template DOCX.
     *
     * Soporta dos modos:
     * - classpath: Copia el recurso a un directorio temporal.
     * - ruta directa: Resuelve respecto a templatesDir.
     *
     * @param templateName Nombre del archivo template.
     * @return Ruta resuelta del template.
     * @throws IOException Si el template no se puede copiar o leer.
     */
    private Path resolveTemplate(String templateName) throws IOException {
        if (templatesDir.startsWith("classpath:")) {
            String classpath = templatesDir.substring("classpath:".length());
            String resourcePath = classpath + "/" + templateName;
            ClassPathResource resource = new ClassPathResource(resourcePath);

            Path tempDir = Files.createTempDirectory("actas-tpl-");
            Path tempFile = tempDir.resolve(templateName);
            Files.copy(resource.getInputStream(), tempFile);
            return tempFile;
        }
        return Paths.get(templatesDir, templateName);
    }

    /**
     * Convierte un objeto a List<Map<String, Object>> de forma segura.
     *
     * @param obj Objeto a convertir (se espera instanceof List).
     * @return Lista de mapas, o lista vacía si la conversión falla.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object obj) {
        if (obj instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /**
     * Convierte un objeto a Map<String, Object> de forma segura.
     *
     * @param obj Objeto a convertir (se espera instanceof Map).
     * @return Mapa, o mapa vacío si la conversión falla.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * Descompone la fecha ISO (yyyy-MM-dd) en dia, mes y anio.
     *
     * Agrega al mapa las claves "dia", "mes" y "anio" con formato de dos dígitos.
     * Si la fecha no se puede parsear, establece valores vacíos.
     *
     * @param datos Mapa de datos donde se agregarán las claves de fecha.
     */
    private void prepararFecha(Map<String, Object> datos) {
        String fechaStr = datos.getOrDefault("fecha", "").toString();
        try {
            LocalDate fecha = LocalDate.parse(fechaStr, DATE_FMT);
            datos.put("dia", String.format("%02d", fecha.getDayOfMonth()));
            datos.put("mes", String.format("%02d", fecha.getMonthValue()));
            datos.put("anio", String.valueOf(fecha.getYear()));
        } catch (Exception e) {
            datos.put("dia", "");
            datos.put("mes", "");
            datos.put("anio", "");
        }
    }
}
