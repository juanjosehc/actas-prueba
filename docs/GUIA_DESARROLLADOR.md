# Guía para Nuevos Desarrolladores — Actas GLPI

Onboarding del sistema. Objetivo: entender qué hace, dónde está cada cosa y cómo fluye una solicitud sin explicaciones adicionales.

---

## 1. Qué es esto en una línea

Aplicación que captura datos de **actas de entrega, devolución y formateo seguro** en un formulario web, genera documentos Word (DOCX) a partir de **plantillas** y los descarga como ZIP. Los equipos y usuarios se autocompletan consultando la **API REST de GLPI**.

## 2. Dónde empezar a leer

Orden recomendado:

1. `README.md` — visión general, requisitos, cómo correr.
2. `ARQUITECTURA.md` — capas, paquetes, integración GLPI, motor DOCX.
3. `DOCUMENTACION_TECNICA.md` — especificación fina: endpoints, DTOs, queries GLPI, ZIP, seguridad.
4. `FLUJO_FUNCIONAL.md` — paso a paso funcional de cada acta.
5. El código en este orden: `config/AppConfig` → un `controller` → su `service` → `DocumentoWordService` → `DocxTemplateEngine` → frontend `ui.js`/`autocomplete.js`/una página.

## 3. Arquitectura en dos párrafos

**Frontend** (estático, sin framework): HTML+JS vanilla con Tailwind/FlyonUI. Tres páginas (`pages/acta-*.html`), cada una carga `config.js` (URL del backend) → `ui.js` (utilidades compartidas) → `autocomplete.js` → su lógica específica (`app.js`, `devolucion.js`, `formateo.js`). Valida campos, construye el JSON y descarga el ZIP.

**Backend** (Java 21 + Spring Boot): recibe el JSON en un **controller** (valida con `@Valid`), lo orquesta un **service** que convierte el DTO a `Map`, `DocumentoWordService` prepara los datos y llama a `DocxTemplateEngine` (reemplaza `{{ var }}` en el DOCX a nivel de run), `ZipService` empaqueta, y el `GET /descargar-acta` sirve el ZIP. `EquipoService`/`UsuarioService` consultan GLPI vía `GlpiClient`.

## 4. Cómo fluye una solicitud (request flow)

```
Página acta-entrega.html
  → escribir serial, click Buscar → GET /equipo/{serial} → Se rellena marca/tipo/modelo
  → escribir persona (3+ chars) → GET /usuarios?texto= → sugerencia (nombre + login)
  → completar form + checklist → click Generar
  → POST /generar-acta
      → ActaController.@Valid → ActaService
      → DocumentoWordService.generarActa + generarChecklist (plantillas)
      → ZipService (ActaLista_{serial}_{asunto}.zip)
      → { success, nombre_zip }
  → GET /descargar-acta/{nombre_zip} → Blob → <a download> → descarga
```

Mismo patrón para `/generar-devolucion` (1 DOCX) y `/generar-formateo-seguro` (1 DOCX, máx. 4 equipos).

## 5. Dónde vive cada cosa (mapa de archivos)

| Tarea | Archivo |
|-------|---------|
| Endpoints | `backend/.../controller/` (5 controllers) |
| Validación backend | DTOs en `dto/request/` + `GlobalExceptionHandler` |
| Generación DOCX | `service/DocumentoWordService.java` (nombres de plantilla FIJOS) |
| Motor de reemplazo | `service/DocxTemplateEngine.java` |
| Zip / descarga | `service/ZipService.java` + `controller/ActaController.descargarActa` |
| GLPI equipos | `service/EquipoService.java` (`cpuCorto`, campos 23/4/40/17) |
| GLPI usuarios | `service/UsuarioService.java` (multi-término, campos 9/34/1/2) |
| HTTP + tokens GLPI | `service/GlpiClient.java` (timeouts, `initSession`) |
| Config | `resources/application.yml`, `config/AppConfig.java` (.env), `config/CorsConfig.java` |
| URL del backend (frontend) | `frontend/js/config.js` (`API_URL`) |
| Validación y utilidades JS | `frontend/js/ui.js` (`validarCampo`, `renumerarEquipos`, `buscarEquipoBloque`, `validarEquiposPorBloque`, `mostrarMensaje`) |
| Autocompletado usuarios | `frontend/js/autocomplete.js` (`initAutocomplete`) |
| Lógica por página | `frontend/js/app.js`, `frontend/js/devolucion.js`, `frontend/js/formateo.js` |
| Layout/estilos | `frontend/css/styles.css` + `frontend/css/app.css` (se compila a `output.css`) |

## 6. Variables de entorno, GLPI y tokens

Definidas en `.env` (raíz) o variables del sistema (prioridad):

| Variable | Rol |
|----------|-----|
| `GLPI_URL` | Base de la API REST, ej. `http://host/glpi/apirest.php` |
| `GLPI_APP_TOKEN` | App-Token (obligatorio) |
| `GLPI_USER_TOKEN` | User-Token (obligatorio) |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend en el despliegue |

**Flujo de autenticación GLPI**: `GlpiClient.iniciarSesion()` → `GET {url}/initSession` con headers `App-Token` y `Authorization: user_token {token}` → `session_token`; luego `search()` usa `Session-Token`. Timeouts: 10 s conexión, 30 s request.

**Campos GLPI usados:**

| Entidad | Campos |
|---------|--------|
| Computer | `5` serial (filtro), `23` fabricante, `4` tipo, `40` modelo, `17` CPU |
| User | `2` id, `1` login/name, `9` firstname, `34` realname |

**Búsqueda de usuarios (multi-término):** el texto se separa por espacios; cada término hace `contains` sobre firstname OR realname OR login, y los grupos se enlazan con AND. "Julian Celis" encuentra `firstname="Julian Alejandro"` + `realname="Celis Valderrama"` (login `JuliCeli`). Ver query completa en `DOCUMENTACION_TECNICA.md` §8.3.

## 7. Convenciones que debes respetar

- **Nombres de plantillas DOCX son constantes en `DocumentoWordService`** (`Acta de Entrega 2 2 - copia.docx`, `ListaChequeo.docx`, `ActaDevolucion.docx`, `ActaFormateoSeguro.docx`). Renombrarlas rompe la generación.
- **Placeholders `{{ var }}`**: todo placeholder nuevo debe rellenarse en `DocumentoWordService`; si no, queda `{{ var }}` literal en el documento.
- **Límites en dos lugares**: frontend limita (3 equipos entrega/devolución, 4 formateo; 9 hardware, 3 otros) y backend rellena hasta el límite de plantilla (10/11/10/4). Cambiar un límite requiere tocar ambos.
- **Fecha**: el frontend envía `YYYY-MM-DD`; el backend la descompone en `dia/mes/anio` (formato de 2 dígitos).
- **Indexación para plantillas**: `eq_N_*`, `hw_N_*`, `ot_N_*`, `chk_N_si/no`. El backend escribe TODOS los slots hasta el máximo, vacíos si no vienen.
- **Nombres de ZIP**: `ActaLista_`, `Devolucion_`, `FormateoSeguro_` + serial del primer equipo + asunto/motivo limpiado (`[^a-zA-Z0-9]`). Sin equipos → `SinSerial`.
- **Sin framework JS**: mantén JS vanilla; `ui.js` es el lugar compartido, no dupliques helpers por página.
- **Errores**: nunca filtes stacktrace/servidor al cliente; loguea en servidor y devuelve mensaje genérico (patrón de `GlobalExceptionHandler`).

## 8. Cómo correr y probar

```bash
# Backend (en backend/)
cp ../.env.example ../.env   # completar tokens
mvn spring-boot:run          # arranca en :8001

# Frontend (en frontend/)
npm install
# abrir pages/acta-entrega.html con Live Server (5501)
```

**Verificación rápida sin UI:**

```bash
curl -s "http://127.0.0.1:8001/equipo/ABC123XYZ"          # → {marca,tipo,modelo}
curl -s "http://127.0.0.1:8001/usuarios?texto=julian celis"  # → [{id,nombreCompleto,login}]
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"fecha":"2026-08-27","entregado_a":"A","cargo_recibe":"B","entregado_por":"C","cargo_entrega":"D","asunto":"test","numero_sac":"1","sistema_operativo":"Windows 11","equipos":[]}' \
  http://127.0.0.1:8001/generar-acta
```

No hay tests automatizados (`backend/src/test` vacío). La comprobación es manual: generar una acta real y abrir el DOCX para confirmar que los placeholders se reemplazaron.

## 9. Zonas frágiles (vete con cuidado)

1. **Motor DOCX a nivel de run** — si cambias el formato de un párrafo en Word, el placeholder puede dividirse en más runs; re-probar siempre.
2. **`AppConfig` y la ruta `../.env`** — depende del directorio de trabajo; si ejecutas el jar desde otra carpeta, no encontrará `.env`.
3. **`EquipoService`/`UsuarioService` tragan excepciones sin log** — un fallo de GLPI se ve como "datos vacíos" en el UI y nada en los logs.
4. **Descarga con 200 + error body** — si el ZIP no existe, `/descargar-acta` devuelve HTTP 200 con `{success:false}`; el frontend lo maneja así.
5. **Sin autenticación** — endpoints abiertos en la red donde corra el backend.
6. **Sin Docker oficial** — `docker.md` es una propuesta; no hay `Dockerfile`/`docker-compose.yml` versionados.

## 10. Si necesitas profundizar

| Documento | Qué tiene |
|-----------|-----------|
| `DOCUMENTACION_TECNICA.md` | Endpoints con ejemplos JSON, queries GLPI exactas, seguridad, hallazgos |
| `FLUJO_FUNCIONAL.md` | Diagramas de cada acta, checklist, ZIP, validaciones |
| `ARQUITECTURA.md` | Capas, paquetes, integración |
| `docs/MANTENIMIENTO.md` | Puntos críticos, configuración, despliegue |