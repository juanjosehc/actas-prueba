# Guía de Pruebas Manuales — Actas GLPI

Checklist de QA para validar el sistema de generación de actas (entrega, devolución y formateo seguro) antes de liberar una versión o tras tocar código, plantillas o configuración. Todo resultado se apoya en el comportamiento real del código: los valores esperados que se listan aquí son los que produce el backend actual.

---

## 1. Cuándo ejecutar

- Antes de marcar un tag/versión (`Stable X.Y`).
- Después de modificar: `DocumentoWordService`, las plantillas DOCX en `resources/plantillas/`, DTOs, validaciones del frontend, `GlpiClient`/`EquipoService`/`UsuarioService`, o la configuración de CORS/puertos.
- Después de un despliegue (Docker u otro): el smoke test de la sección 2.

## 2. Prerrequisitos y arranque

| Requisito | Detalle |
|-----------|---------|
| Backend corriendo | `cd backend && mvn spring-boot:run` (puerto 8001). En despliegue Docker: `docker compose up -d`. |
| `.env` con tokens válidos | `GLPI_URL`, `GLPI_APP_TOKEN`, `GLPI_USER_TOKEN`. Sin tokens el backend no arranca. |
| Un serial real en GLPI | Usar uno de los equipos de la organización para las pruebas de autocompletado. En esta guía se llama `ABC123XYZ`. |
| Un navegador | Para las pruebas de UI. Idealmente abrir la consola (F12) para ver errores CORS/red. |

### Smoke test de la API (2 minutos)

```bash
# 1. Equipo por serial (debe existir en GLPI)
curl -s http://127.0.0.1:8001/equipo/ABC123XYZ
# → {"marca":"DELL","tipo":"Notebook","modelo":"Latitude 5440 (Core i5)"}

# 2. Búsqueda de usuarios (mín. 3 caracteres)
curl -s "http://127.0.0.1:8001/usuarios?texto=julian"
# → [{"id":12,"nombreCompleto":"Julian Alejandro Celis","login":"JuliCeli"}]

# 3. Generar acta de entrega mínima
curl -s -X POST http://127.0.0.1:8001/generar-acta -H "Content-Type: application/json" -d '{
  "fecha":"2026-08-27","entregado_a":"Juan Perez","cargo_recibe":"Analista",
  "entregado_por":"Maria Lopez","cargo_entrega":"Coordinador TI",
  "asunto":"entrega","numero_sac":"SAC-001","sistema_operativo":"Windows 11",
  "hardware":[{"tipo":"Monitor","descripcion":"24 pulgadas","programa":"Office"}],
  "equipos":[{"serial":"ABC123XYZ","marca":"DELL","tipo":"Notebook","modelo":"Latitude 5440","inventario":"G-1234"}],
  "checklist":{"chk_1":true,"chk_2":false}
}'
# → {"success":true,"mensaje":"Documentación generada correctamente","nombre_zip":"ActaLista_ABC123XYZ_entrega.zip"}
# IMPORTANTE: respuesta exitosa pero SIN "success":true = error de validación (HTTP 400) o interno (500).
```

Si el paso 1 o 2 devuelve `{"marca":"","tipo":"","modelo":""}` o `[]` con GLPI sano, el problema es red/tokens: el backend **no loguea** esos fallos (ver `docs/MANTENIMIENTO.md` punto 7).

---

## 3. Acta de entrega

DTO de entrada (`ActaRequest`): `fecha`, `entregado_a`, `cargo_recibe`, `entregado_por`, `cargo_entrega`, `asunto`, `numero_sac`, `sistema_operativo` (obligatorios) + `hardware[{tipo,descripcion,programa}]`, `equipos[{serial,marca,tipo,modelo,inventario}]`, `checklist{chk_N: bool}`, `observaciones`.

### 3.1 Casos positivos

| # | Caso | Pasos | Resultado esperado |
|---|------|-------|--------------------|
| E1 | Generación completa | Cargar formulario con 1 equipo, 1 hardware, checklist marcado en ciertos ítems, SO Windows 11 | ZIP `ActaLista_{serial}_{asunto}.zip` descarga automática. Contiene `ActaEntrega_*.docx` y `Checklist_*.docx` |
| E2 | Máximos de la página | 3 equipos, 9 hardware | Se generan ambos DOCX sin error; las tablas salen completas hasta el límite rellenado |
| E3 | Sin equipos | Dejar la lista de equipos vacía (el resto obligatorio completo) | Genera con `SinSerial`: `ActaLista_SinSerial_{asunto}.zip`. El DOCX sale con las filas de equipos vacías |
| E4 | Autocompletado por serial | Escribir serial existente y clic Buscar | Rellena marca/tipo/modelo en la fila; modelo incluye CPU abreviada (ej. `Latitude 5440 (Core i5)`) |
| E5 | Sistema operativo | Generar con `Windows 10`, luego `Windows 11` y `Mac OS` | En el checklist DOCX se marca con `X` la casilla correspondiente (solo una) |
| E6 | Checklist negativo | Marcar varios ítems como "No" | En el DOCX, `chk_N_no` lleva `X` y `chk_N_si` queda vacío para esos ítems |

### 3.2 Casos negativos y validaciones

| # | Caso | Pasos | Resultado esperado |
|---|------|-------|--------------------|
| E7 | Campos obligatorios vacíos | Enviar `{}` (o borrar fecha) | HTTP 400 con mensajes unidos por coma, ej. `"La fecha es obligatoria, El campo entregado_a es obligatorio, ..."` |
| E8 | Fecha mal formada | `fecha: "27/08/2026"` (formato DD/MM/YYYY) | Se acepta la petición (no valida formato) pero el DOCX sale con el bloque de fecha **vacío** (`dia/mes/anio` en blanco) |
| E9 | Fecha correcta | `fecha: "2026-08-27"` | En el DOCX: `dia=27`, `mes=08`, `anio=2026` (mes y día con dos dígitos) |
| E10 | Asunto con caracteres raros | `asunto: "Entrega #1 - Backup!"` | Nombre limpio: `ActaLista_{serial}_Entrega1Backup.zip` (se elimina todo lo que no sea `[a-zA-Z0-9]`) |
| E11 | más de 10 equipos por API | POST con 11 equipos en `equipos` | No valida límite en backend para entrega; se genera pero la plantilla solo imprime hasta `eq_10`. **No usar como flujo normal**, el frontend limita a 3 |
| E12 | Equipo inexistente en GLPI | Serial que no existe | Los campos quedan vacíos (backend devuelve `{"marca":"","tipo":"","modelo":""}`). El usuario debe digitarlos manualmente |

### 3.3 Checklist DOCX (variable `sistema_operativo`)

El checkbox de SO se marca según coincidencia **exacta**:

| Valor enviado | Casilla marcada |
|---------------|-----------------|
| `Windows 10` | `win10` |
| `Windows 11` | `win11` |
| `Mac OS` | `macos` |
| `Windows` (o cualquier otro) | ninguna (todas vacías) |

---

## 4. Acta de devolución

DTO de entrada (`DevolucionRequest`): solo `fecha` obligatoria. `recibido_por`, `entregado_por`, `cargo_recibe`, `cedula`, `area_recibe`, `motivo`, `cargo_entrega`, `equipos[{..., estado}]`, `hardware[{tipo}]`, `observaciones`.

| # | Caso | Pasos | Resultado esperado |
|---|------|-------|--------------------|
| D1 | Generación completa | Formulario con 1 equipo (con estado, ej. "Dañado"), 1 otro elemento, motivo | ZIP `Devolucion_{serial}_{motivo}.zip` con 1 DOCX |
| D2 | `motivo` con espacios/tildes | `motivo: "cambio de puesto"` | Nombre limpio: `Devolucion_{serial}_cambiodepuesto.zip` |
| D3 | Sin equipos | Lista vacía, motivo escrito | `Devolucion_SinSerial_{motivo}.zip`; DOCX con filas vacías |
| D4 | Estado de cada equipo | Cargar 2 equipos con estados distintos | Cada fila del DOCX muestra su `estado` (`eq_N_estado`) |
| D5 | Otros elementos | Cargar 3 "otros elementos" | Se imprimen en `ot_1_tipo` ... hasta los cargados (máx 10 en plantilla) |
| D6 | Sin `cedula` | Dejar vacío | No hay error (campo no obligatorio); el DOCX sale con la cédula en blanco |
| D7 | Fecha ausente | POST sin `fecha` | HTTP 400 `"La fecha es obligatoria"` |

---

## 5. Acta de formateo seguro

DTO de entrada (`FormateoSeguroRequest`): `fecha`, `entregado_a`, `cargo_recibe`, `entregado_por`, `cargo_entrega`, `asunto` (obligatorios) + `equipos[{..., gb}]` con **máximo 4** (`@Size(max = 4)` en backend).

| # | Caso | Pasos | Resultado esperado |
|---|------|-------|--------------------|
| F1 | Generación completa | 1 equipo con gigas (ej. `gb: "512"`), asunto | ZIP `FormateoSeguro_{serial}_{asunto}.zip` con 1 DOCX |
| F2 | Máximo permitido | 4 equipos | Genera normal (4 filas en plantilla) |
| F3 | Más de 4 equipos por API | POST con 5 equipos | HTTP 400 `"Máximo 4 equipos (capacidad de la plantilla)"` |
| F4 | Sin equipos | Lista vacía | `FormateoSeguro_SinSerial_{asunto}.zip`; las 4 filas vacías en el DOCX |
| F5 | Campo `gb` | Equipos con `gb` "256"/"512" | Cada fila muestra su `gb` (`eq_N_gb`) |
| F6 | Firma del acta | Cargar `entregado_por` | El DOCX muestra el nombre en el campo de firma **`entrega_por`** (alias automático del backend) |
| F7 | Fecha faltante | POST sin `fecha` | HTTP 400 |

---

## 6. Descarga del ZIP y verificación del DOCX

### 6.1 Descarga

```bash
# El nombre_zip viene en la respuesta del POST. Descargar:
curl -s -o acta.zip "http://127.0.0.1:8001/descargar-acta/ActaLista_ABC123XYZ_entrega.zip"
# Ver contenido:
tar -tf acta.zip        # o abrir con 7-Zip/Explorer
# → ActaEntrega_ABC123XYZ_entrega.docx
# → Checklist_ABC123XYZ_entrega.docx
```

| # | Caso | Resultado esperado |
|---|------|--------------------|
| Z1 | Descargar un ZIP existente | Se descarga el archivo; header `Content-Disposition: attachment` |
| Z2 | Descargar un ZIP inexistente | HTTP **200** con body `{"success":false,"mensaje":"Archivo no encontrado"}`. **No es error de red**: el frontend muestra el mensaje del body |
| Z3 | `nombreZip` con `../` o `/` | HTTP 400 (el backend rechaza path traversal) |

> Nota: los ZIP/DOCX se guardan en `app.generated-dir`. Quedan en el disco; el sistema no los borra.

### 6.2 Verificación del DOCX en Word

1. Abrir el ZIP y extraer los `.docx`.
2. Abrir cada DOCX en Word.
3. Comprobar:
   - **Sin texto literal `{{ ... }}`** en ninguna parte (ningún placeholder sin reemplazar).
   - Fecha descompuesta correcta (día/mes/año de dos dígitos).
   - Tablas de equipos/hardware/checklist alineadas con lo ingresado.
   - Checkboxes como `X` (no símbolos ni caracteres rotos).
   - Que el formato (negritas, fuentes, bordes de tabla) se conservó igual que en la plantilla original.

Caso que pilla con frecuencia: si se edita una plantilla en Word y se pega un placeholder nuevo, Word puede partir el texto en varios *runs* y el motor no lo reemplaza, quedando `{{ var }}` literal. Ver `docs/GUIA_EDITAR_PLANTILLAS.md` sección 8.

---

## 7. Casos de GLPI e integración

| # | Caso | Pasos | Resultado esperado |
|---|------|-------|--------------------|
| G1 | Búsqueda multi-término | `texto=julian celis` | Usuarios cuyo firstname/realname/login contengan **ambos** términos (OR por término, AND entre términos), ej. "Julian Alejandro Celis (JuliCeli)" |
| G2 | Menos de 3 caracteres | `texto=ju` | Lista vacía (`[]`). El backend exige mínimo 3 |
| G3 | GLPI con timeout/red caída | Desconectar la red o apuntar `GLPI_URL` a un host muerto | `GET /equipo/...` devuelve valores vacíos y `/usuarios` devuelve `[]`: la UI "no falla" pero no hay datos. Sin logs en backend (limitación conocida) |
| G4 | Tokens inválidos | Poner un `GLPI_APP_TOKEN` falso | Backend no arranca (token sin default) o `search()` lanza excepción capturada → resultados vacíos |
| G5 | Formato de CPU | Serial cuyo modelo trae "Intel(R) Core(TM) i7-1xxxCPU" | `sistema operativo`—no; el **modelo** del equipo se abrevia: `Core i7-...` (quita `Intel(R)`/`Core(TM)`, conserva el generador de la familia). Verificar en `EquipoService.cpuCorto` |

---

## 8. Validaciones del frontend (UI)

| # | Verificación | Esperado |
|---|--------------|----------|
| U1 | Equipos: máximo 3 en entrega/devolución, 4 en formateo | El botón "Agregar" se deshabilita o muestra mensaje en el límite |
| U2 | Hardware entrega: máx. 9 | Mismo comportamiento |
| U3 | Otros elementos devolución: máx. 3 | Mismo comportamiento |
| U4 | Checklist: 36 ítems | Al marcar en la página, el DOCX refleja `si`/`no` |
| U5 | Autocompletado personas | Sugiere `nombre + login`; al seleccionar llena el input |
| U6 | Fecha visible DD-MM-YYYY pero envía YYYY-MM-DD | Generar y verificar `dia/mes/anio` en el DOCX |
| U7 | Campos obligatorios en blanco | La página marca los campos con error (no llega al backend) |
| U8 | Responsividad en 1366×768 y 1920×1080 | Sin desbordes en el layout de formateo (grid de 2 columnas) |

---

## 9. Checklist final (resumen para firmar la versión)

```
[ ] Smoke API: /equipo, /usuarios, las 3 generaciones responden
[ ] Acta de entrega: genera ZIP con 2 DOCX correctos
[ ] Checklist: SO y 36 checkboxes correctos
[ ] Devolución: 1 DOCX con estados y otros elementos
[ ] Formateo: máx 4 equipos, campo gb, alias entrega_por
[ ] Descarga: ZIP inexistente → body {success:false} (no error crudo)
[ ] Sin "{{ ... }}" literal en ningún DOCX
[ ] Validaciones: obligatorios vacíos → 400; 5 equipos formateo → 400
[ ] GLPI: multi-término funciona; serial inválido → campos vacíos (no crash)
[ ] Frontend: límites 3/9/3/4 aplicados; fecha DD-MM-YYYY visible
[ ] No se regresaron stacktraces al cliente (mensajes genéricos)
```

Si algún punto de los "Resultado esperado" de esta guía no coincide con la realidad, revisar el código correspondiente antes de liberar: el código fuente es la única fuente de verdad.