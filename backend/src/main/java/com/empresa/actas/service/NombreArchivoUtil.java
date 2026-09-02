package com.empresa.actas.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidad central de construcción de nombres de archivo documentales.
 *
 * Punto único donde se definen:
 * <ul>
 *   <li>Normalización de serial (MAYÚSCULAS, sin espacios).</li>
 *   <li>Normalización de nombre de usuario (sin tildes, sin símbolos, camelCase).</li>
 *   <li>Formato base de archivo: {@code [TIPO_ACTA]_[SERIAL]_[NOMBRE]}.</li>
 * </ul>
 *
 * Toda generación de DOCX y ZIP debe pasar por {@link #nombreBase}; nunca
 * duplicar esta lógica en servicios individuales.
 *
 * La búsqueda en GLPI no usa esta clase: EquipoService recibe el serial
 * tal cual lo escribe el usuario, por lo que sigue funcionando con
 * minúsculas o cualquier combinación de mayúsculas/minúsculas.
 */
public final class NombreArchivoUtil {

    /** Segmento por defecto cuando no hay serial. */
    private static final String SIN_SERIAL = "SINSERIAL";

    /** Segmento por defecto cuando no hay nombre. */
    private static final String SIN_NOMBRE = "SINNOMBRE";

    private NombreArchivoUtil() {
        // Clase de utilidad, no instanciable.
    }

    /**
     * Normaliza un serial para documentación: MAYÚSCULAS y sin espacios.
     *
     * @param serial Serial en cualquier formato ("abc123", "AbC123", "ABC 123").
     * @return Serial normalizado ("ABC123"), o cadena vacía si es null.
     */
    public static String normalizarSerial(String serial) {
        if (serial == null) {
            return "";
        }
        return serial.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Normaliza un nombre de usuario para nombres de archivo.
     *
     * Elimina tildes y símbolos, colapsa espacios y aplica camelCase.
     *
     * Ejemplo: "Juan José Hernández Correa" → "JuanJoseHernandezCorrea"
     *
     * @param nombre Nombre en cualquier formato ("Juán JOSE Hernández").
     * @return Nombre sanitizado y en camelCase, o cadena vacía si es null/blank.
     */
    public static String normalizarNombre(Object nombre) {
        if (nombre == null) {
            return "";
        }
        String texto = nombre.toString();
        if (texto.isBlank()) {
            return "";
        }

        // 1. Separar diacríticos y eliminar marcas (tildes, ñ → n).
        String sinDiacriticos = Normalizer
                .normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // 2. Cualquier carácter que no sea letra o dígito se vuelve separador.
        String soloAlfaNumeric = sinDiacriticos
                .replaceAll("[^\\p{L}\\p{N}]", " ");

        // 3. Dobles espacios ≈ un solo separador.
        String[] tokens = soloAlfaNumeric
                .trim()
                .split("\\s+");

        // 4. camelCase: primera letra mayúscula, resto minúscula.
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                sb.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /**
     * Construye el nombre base de un archivo documental.
     *
     * Formato: {@code [TIPO_ACTA]_[SERIAL]_[NOMBRE]}
     *
     * Ejemplo: "ActaEntrega_ABC123XYZ_JuanJoseHernandezCorrea"
     *
     * @param tipoActa Prefijo del tipo de acta ("ActaEntrega", "ActaDevolucion",
     *                 "ActaFormateoSeguro", "Checklist").
     * @param serial   Serial del equipo.
     * @param nombre   Nombre del usuario (entregado_a o entregado_por según tipo).
     * @return Nombre base normalizado, seguro para Windows y Linux.
     */
    public static String nombreBase(String tipoActa, String serial, Object nombre) {
        String s = normalizarSerial(serial);
        String n = normalizarNombre(nombre);

        if (s.isEmpty()) {
            s = SIN_SERIAL;
        }
        if (n.isEmpty()) {
            n = SIN_NOMBRE;
        }
        return tipoActa + "_" + s + "_" + n;
    }
}