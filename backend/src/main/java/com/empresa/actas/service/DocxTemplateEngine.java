package com.empresa.actas.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de reemplazo de placeholders en documentos Word (DOCX).
 *
 * Sintaxis de placeholders: {{ nombre_variable }}
 *
 * Algoritmo de reemplazo:
 * 1. Copiar el template al archivo de salida.
 * 2. Abrir el DOCX con Apache POI.
 * 3. Para cada párrafo (en cuerpo del documento y en tablas):
 *    a. Concatenar el texto de todos los "runs" del párrafo.
 *    b. Si el texto concatenado contiene "{{", procesar reemplazos.
 *    c. Construir un mapa de posiciones de cada run en el texto concatenado.
 *    d. Buscar todos los placeholders con regex.
 *    e. Para cada run, reconstruir su texto verificando si contiene
 *       partes de algún placeholder.
 *    f. Si el run inicia un placeholder, reemplazar todo el placeholder
 *       con el valor correspondiente.
 *    g. Si el run solo contiene parte intermedia/final del placeholder,
 *       eliminar esos caracteres.
 * 4. Guardar el documento modificado.
 *
 * Por qué nivel de run:
 * Los documentos Word fragmentan el texto en "runs" cuando se aplica
 * formato diferente (negrita, color, tamaño). Un solo placeholder
 * puede estar dividido en 3-4 runs. Este enfoque preserva el formato
 * original de cada run sin necesidad de fusionarlos.
 *
 * Límites conocidos:
 * - No maneja placeholders en notas al pie ni encabezados/pies de página.
 * - Un placeholder no puede cruzar más de un párrafo.
 */
public class DocxTemplateEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * Procesa un template DOCX reemplazando todos los placeholders con los valores dados.
     *
     * @param templatePath Ruta al archivo template DOCX.
     * @param variables    Mapa de nombre → valor para reemplazar.
     * @param outputPath   Ruta donde escribir el DOCX resultante.
     * @return La ruta del archivo generado (misma que outputPath).
     * @throws IOException Si hay error al leer o escribir archivos.
     */
    public static Path processTemplate(
            Path templatePath,
            Map<String, String> variables,
            Path outputPath
    ) throws IOException {

        Files.copy(templatePath, outputPath, StandardCopyOption.REPLACE_EXISTING);

        try (FileInputStream fis = new FileInputStream(outputPath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                replaceInParagraph(paragraph, variables);
            }

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            replaceInParagraph(paragraph, variables);
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                document.write(fos);
            }
        }

        return outputPath;
    }

    /**
     * Reemplaza placeholders en un solo párrafo a nivel de run.
     *
     * Algoritmo detallado:
     * 1. Construir boundary[]: posición de inicio de cada run en el texto concatenado.
     * 2. Concatenar todo el texto del párrafo.
     * 3. Si no contiene "{{", saltar (early return).
     * 4. Buscar todos los placeholders con regex, guardando posiciones [start, end].
     * 5. Para cada run:
     *    - Determinar qué parte del texto le corresponde (runStart..runEnd).
     *    - Verificar si algún placeholder "toca" este run.
     *    - Si no toca ninguno, mantener el texto original.
     *    - Si toca, reconstruir el texto karakter por karakter:
     *      * Si el caracter es parte intermedia/final de placeholder → omitir.
     *      * Si el caracter inicia un placeholder → insertar el valor completo.
     *      * Si no es parte de placeholder → mantener el caracter.
     *
     * @param paragraph Párrafo a procesar.
     * @param variables Mapa de nombre → valor.
     */
    private static void replaceInParagraph(
            XWPFParagraph paragraph,
            Map<String, String> variables
    ) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }

        int n = runs.size();
        int[] boundary = new int[n + 1];
        StringBuilder concat = new StringBuilder();
        for (int r = 0; r < n; r++) {
            boundary[r] = concat.length();
            String t = runs.get(r).text();
            concat.append(t != null ? t : "");
        }
        boundary[n] = concat.length();

        String full = concat.toString();
        if (!full.contains("{{")) {
            return;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(full);
        if (!matcher.find()) {
            return;
        }

        matcher.reset();
        List<int[]> matchRanges = new ArrayList<>();
        List<String> matchValues = new ArrayList<>();
        while (matcher.find()) {
            matchRanges.add(new int[]{matcher.start(), matcher.end()});
            matchValues.add(variables.getOrDefault(matcher.group(1), ""));
        }

        for (int r = 0; r < n; r++) {
            int runStart = boundary[r];
            int runEnd = boundary[r + 1];

            boolean touches = false;
            for (int[] range : matchRanges) {
                if (range[0] < runEnd && range[1] > runStart) {
                    touches = true;
                    break;
                }
            }
            if (!touches) {
                continue;
            }

            StringBuilder newRunText = new StringBuilder();
            int pos = runStart;

            while (pos < runEnd) {
                int[] hitRange = null;
                int hitIdx = -1;
                for (int m = 0; m < matchRanges.size(); m++) {
                    int[] range = matchRanges.get(m);
                    if (pos >= range[0] && pos < range[1]) {
                        hitRange = range;
                        hitIdx = m;
                        break;
                    }
                }

                if (hitRange == null) {
                    newRunText.append(full.charAt(pos));
                    pos++;
                } else {
                    if (r == findRun(boundary, n, hitRange[0])) {
                        newRunText.append(matchValues.get(hitIdx));
                    }
                    pos = hitRange[1];
                }
            }

            runs.get(r).setText(newRunText.toString(), 0);
        }
    }

    /**
     * Determina a qué run pertenece una posición dada en el texto concatenado.
     *
     * @param boundary Array de posiciones de inicio de cada run.
     * @param n        Número total de runs.
     * @param position Posición en el texto concatenado.
     * @return Índice del run que contiene la posición.
     */
    private static int findRun(int[] boundary, int n, int position) {
        for (int r = 0; r < n; r++) {
            if (position >= boundary[r] && position < boundary[r + 1]) {
                return r;
            }
        }
        return n - 1;
    }
}
