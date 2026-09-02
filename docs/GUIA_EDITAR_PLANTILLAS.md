# Guía para Editar Plantillas Word — Actas GLPI

Cómo funcionan las plantillas DOCX, qué placeholders contienen, y cómo editarlas o agregar variables nuevas sin romper la generación de documentos.

---

## 1. Qué son y dónde viven

Las plantillas son archivos **DOCX normales** (Word) que contienen el texto, tablas y formato de las actas. Los valores se rellenan mediante **placeholders** `{{ nombre }}` que el backend reemplaza al generar.

- **Ubicación:** `backend/src/main/resources/plantillas/` (viajan dentro del JAR vía `classpath:plantillas`).
- **Motor de reemplazo:** `DocxTemplateEngine` recorre el documento y sustituye cada placeholder **a nivel de run** de Word (explicado en la sección 7).
- **Quién los usa:** `DocumentoWordService` — cada método elige la plantilla, prepara los datos y llama al motor.

⚠️ **Regla de oro:** el **nombre del archivo** es fijo en el código. Si se renombra o mueve una plantilla sin actualizar `DocumentoWordService`, la generación falla. El nombre heredado `Acta de Entrega 2 2 - copia.docx` es feo pero **no debe cambiarse** sin tocar el código.

## 2. Las 4 plantillas y sus variables

Tabla de referencia (fuente: `DocumentoWordService`).

### 2.1 `Acta de Entrega 2 2 - copia.docx` → acta de entrega

| Grupo | Variables | Máx | De dónde sale |
|-------|-----------|-----|---------------|
| Cabecera | `{{dia}}`, `{{mes}}`, `{{anio}}` | — | fecha (`YYYY-MM-DD`) descompuesta |
| Campos libre | `{{entregado_a}}`, `{{cargo_recibe}}`, `{{entregado_por}}`, `{{cargo_entrega}}`, `{{asunto}}`, `{{numero_sac}}`, `{{sistema_operativo}}`, `{{observaciones}}` | — | mismos nombres en el DTO `ActaRequest` |
| Hardware | `{{hw_N_tipo}}`, `{{hw_N_descripcion}}`, `{{hw_N_programa}}` | N = 1..11 | lista `hardware` del request |
| Equipos | `{{eq_N_marca}}`, `{{eq_N_tipo}}`, `{{eq_N_modelo}}`, `{{eq_N_serial}}`, `{{eq_N_inventario}}` | N = 1..10 | lista `equipos` del request |

> El código inicializa `hw_12..hw_16` vacíos, pero la plantilla solo usa hasta `hw_11` (y el service solo rellena hasta 11).

### 2.2 `ListaChequeo.docx` → lista de chequeo (segundo DOCX del ZIP de entrega)

| Grupo | Variables | Detalle |
|-------|-----------|---------|
| Fecha | `{{dia}}`, `{{mes}}`, `{{anio}}` | igual que entrega |
| SO | `{{win10}}`, `{{win11}}`, `{{macos}}` | `X` si `sistema_operativo` coincide **exactamente** con `Windows 10` / `Windows 11` / `Mac OS`; vacío si no |
| Checkboxes | `{{chk_N_si}}`, `{{chk_N_no}}` | N = 1..36; `X` en `_si` si el ítem está marcado, si no `X` en `_no` |
| Responsable | `{{responsable_verificacion}}` | alias de `entregado_por` |
| Equipo (solo 1°) | `{{eq_1_marca}}`, `{{eq_1_tipo}}`, `{{eq_1_modelo}}`, `{{eq_1_serial}}`, `{{eq_1_inventario}}` | del **primer** equipo de la lista |
| Resto de cabecera | mismos campos de entrega | `entregado_a`, `cargo_recibe`, etc. |

### 2.3 `ActaDevolucion.docx` → acta de devolución

| Grupo | Variables | Máx |
|-------|-----------|-----|
| Campos libre | `{{recibido_por}}`, `{{entregado_por}}`, `{{cargo_recibe}}`, `{{cedula}}`, `{{area_recibe}}`, `{{motivo}}`, `{{cargo_entrega}}`, `{{observaciones}}` | — |
| Equipos (con estado) | `{{eq_N_marca}}`, `{{eq_N_tipo}}`, `{{eq_N_modelo}}`, `{{eq_N_serial}}`, `{{eq_N_inventario}}`, `{{eq_N_estado}}` | N = 1..10 |
| Otros elementos | `{{ot_N_tipo}}` | N = 1..10 |

> Fuente: `DevolucionRequest` (solo `fecha` obligatoria). Los "otros elementos" equivalen al bloque `hardware` del request, pero aquí solo se usa `tipo` (`OtroElementoItem`).

### 2.4 `ActaFormateoSeguro.docx` → acta de formateo seguro

| Grupo | Variables | Máx |
|-------|-----------|-----|
| Campos libre | `{{entregado_a}}`, `{{cargo_recibe}}`, `{{entregado_por}}`, `{{cargo_entrega}}`, `{{asunto}}` | — |
| Firma | `{{entrega_por}}` | — |
| Equipos (con gigas) | `{{eq_N_marca}}`, `{{eq_N_tipo}}`, `{{eq_N_modelo}}`, `{{eq_N_serial}}`, `{{eq_N_inventario}}`, `{{eq_N_gb}}` | N = 1..4 |

> `{{entrega_por}}` es un **alias**: el DTO no tiene ese campo; `DocumentoWordService.generarFormateoSeguro` copia `entregado_por` → `entrega_por` antes de procesar. No modificar la plantilla puede evitarlo; si se agrega un campo nuevo, se sigue el mismo patrón.

---

## 3. Editar una plantilla existente

Objetivos permitidos: cambiar textos, estilos, negritas, tamaños, logos, bordes de tabla, y **reacomodar placeholders ya existentes** dentro de la misma plantilla.

Pasos:

1. Copiar el archivo de `plantillas/` a una carpeta local y abrirlo en Word. (No editar sobre el JAR ni sobre el del `target/`.)
2. Hacer los cambios de contenido/formato.
3. **No tocar los placeholders `{{ ... }}` que no se van a cambiar**, y no cambiar el nombre del archivo.
4. Guardar como **Documento de Word (.docx)** con el mismo nombre.
5. Copiar de vuelta a `backend/src/main/resources/plantillas/`.
6. Recompilar el backend (`mvn clean package -DskipTests`) o reiniciar `spring-boot:run`.
7. Probar generando una acta real y abriendo el DOCX (sección 6).

> Si se modifica el texto **dentro** de un placeholder (ej. `{{entregado_por }}` con espacio extra, o `{{ ENTREGADO_POR }}`), el motor ya no lo reconoce (`\w+` solo alfanuméricos y guiones bajos, sin espacios). Revisar los corchetes dobles.

### Cambiar solo texto visible

Cambiar el texto de un párrafo que NO contiene placeholders es siempre seguro: el motor solo busca `{{ }}`.

---

## 4. Agregar un placeholder nuevo (paso a paso)

Se requiere tocar **plantilla + backend**. El frontend no participa salvo que el dato provenga de un campo nuevo del formulario.

### Paso 1 — En Word

Escribir el placeholder en el lugar deseado, con el formato que debe tener el valor:

```
{{nueva_var}}
```

Reglas al insertarlo:
- Un solo nombre, solo letras, números y `_`.
- **Escribirlo como texto plano** (teclado), no pegarlo desde otra parte — pegar con formato puede partir el texto en varios *runs* (sección 7).
- No agregar espacios dentro: `{{ nueva_var }}` funciona (el motor ignora espacios), pero manténgase `{{nueva_var}}` por consistencia.

### Paso 2 — Hacer que el backend lo provea

El mapa `vars` que recibe el motor contiene: los campos del DTO (mismos nombres) + las variables preparadas por `DocumentoWordService`. Para que el placeholder no quede literal, el valor debe existir en ese mapa. Tres vías según el caso:

**a) Ya es un campo del DTO** (ej. `motivo` en devolución): no hay que hacer nada en el backend. El placeholder `{{motivo}}` se rellena solo.

**b) Es un dato derivado** (alias, cálculo, transformación): agregar la línea en el método correspondiente de `DocumentoWordService`, justo antes de armar `vars`:

```java
// Datos preparados
datos.put("nueva_var", datos.getOrDefault("entregado_por", ""));
```

**c) Es una variable indexada nueva** (fila nueva de tabla, `eq_N_*`, `hw_N_*`, etc.): hay que declararla en el bucle de inicialización **y** en el de llenado, como hacen los existentes:

```java
// Inicializar todos los slots vacíos
for (int i = 1; i <= 10; i++) {
    datos.put("eq_" + i + "_marca", "");
    ...
    datos.put("eq_" + i + "_campo_nuevo", "");
}
// Llenar los que vienen del request
for (Map<String, Object> eq : eqList) {
    ...
    datos.put("eq_" + idx + "_campo_nuevo", eq.getOrDefault("campo_nuevo", ""));
}
```

### Paso 3 — Recompilar y probar

```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run   # o reiniciar el contenedor Docker
```

Luego generar el acta correspondiente y abrir el DOCX: el valor debe aparecer y **no debe quedar ningún `{{nueva_var}}` literal**.

---

## 5. Si el placeholder sale literal (`{{ x }}` en el DOCX)

Posibles causas, en orden de frecuencia:

1. **El run se dividió** (causa más común): Word partió `{{nueva_var}}` en varios runs (por ejemplo al pegar con formato o cambiar negrita en mitad). El motor reemplaza solo si el placeholder completo está en un **único run**. **Fix:** escribir el placeholder de nuevo como texto plano en Word, de corrido y con un solo estilo.
2. **El nombre no coincide** con la clave del mapa (espacios, mayúsculas, tildes, punto al final). Comparar carácter por carácter con lo que genera el backend.
3. **El dato no llegó**: el campo no existe en el DTO y no se agregó en `DocumentoWordService`. Ver Paso 2.
4. **Plantilla desactualizada**: se editó el `.docx` pero se recompiló sin copiarlo a `resources/plantillas/`.

---

## 6. Verificación end-to-end

1. Generar un acta de prueba desde el formulario (o por API con un payload completo).
2. Descargar el ZIP, extraer el `.docx`.
3. Abrir en Word y comprobar:
   - Ningún `{{ ... }}` literal en el documento.
   - Valores exactos (fecha, nombres, seriales, checkboxes `X`).
   - Formato conservado de la plantilla (bordes de tabla, negritas, fuentes).
4. Pruebas de "no regresión": generar las **4 plantillas** (entrega + checklist viajan juntas; devolución y formateo son separadas) con datos al máximo y con lista vacía.

---

## 7. Cómo funciona el motor (para no romperlo)

`DocxTemplateEngine.processTemplate(templatePath, vars, outputPath)`:

- Abre el DOCX con Apache POI XWPF.
- Recorre **párrafos y tablas**; en cada run busca el patrón `\{\{\s*(\w+)\s*\}\}`.
- Si un run contiene el placeholder completo, lo reemplaza por el valor del mapa.
- **Si el placeholder está partido en dos runs, no lo encuentra** → queda literal.
- Lo que el mapa no tiene, no se procesa: queda como esté en la plantilla.

> En la práctica: trabajar con `mapeo de placeholders` en un solo run. Si la edición de Word reparte `{{x}}` entre `{{` y `x}}`, se ve en el DOCX final como texto literal. Se detecta fácil en la verificación por `Ctrl+B` búsqueda de `{{`.

---

## 8. Personalizar sin recompilar (plantillas externas)

Las plantillas viajan dentro del JAR (`app.templates-dir: classpath:plantillas`). Para editarlas sin reconstruir el backend:

1. Crear una carpeta con las plantillas fuera del JAR (mismos **nombres exactos**).
2. Apuntar la propiedad `app.templates-dir` a esa ruta:
   - `-Dapp.templates-dir=/ruta/plantillas` al arrancar, o
   - variable de entorno `APP_TEMPLATESDIR=/ruta/plantillas` (relaxed binding de Spring).
3. `resolveTemplate` de `DocumentoWordService` detecta que no empieza con `classpath:` y lee directo de esa ruta (sin copia temporal).

Útil en despliegues Docker: montar la carpeta como volumen y cambiar `APP_TEMPLATESDIR`, evitando rebuild.

---

## 9. Aumentar la capacidad de una tabla (ej. más equipos)

El límite es **producto de plantilla + backend + formulario**. Subir de 10 a 12 equipos en la entrega requiere tocar los cuatro:

| Lugar | Qué cambiar |
|-------|-------------|
| Plantilla DOCX | Agregar la fila `12` en la tabla con sus placeholders `eq_12_*` |
| `DocumentoWordService.generarActa` | El bucle de llenado rompe en `idx > 10` → subir a 12 |
| DTO (si aplica límite) | `@Size(max = ...)` (en `FormateoSeguroRequest` ya existe con 4) |
| Frontend | El límite de filas de la página (`app.js`/`formateo.js`) y el mensaje del botón "Agregar" |

> En entrega y devolución la **inicialización** ya llena 16 slots (`hw_`) o 10 (`eq_`, `ot_`); para `hw` basta subir el `if (idx > 11)` y la plantilla. En equipos el bucle `for (int i = 1; i <= 10; i++)` también debe crecer.

## 10. Checklist de cambios en plantillas

```
[ ] Mantuve el nombre exacto del archivo (constantes de DocumentoWordService)
[ ] No partí ningún placeholder entre runs (texto plano, un solo estilo)
[ ] Los `{{ }}` quedaron sin espacios raros dentro
[ ] Todo placeholder que aparece en Word tiene valor en el mapa del backend
[ ] Recompilé / reinicié el backend
[ ] Generé las 4 plantillas y verifiqué en Word: sin {{ }} literal
[ ] Probé caso al máximo y caso vacío
[ ] Si agregué filas: revisé límites en plantilla, service, DTO y frontend
```