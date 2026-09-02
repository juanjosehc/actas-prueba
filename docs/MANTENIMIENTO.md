# Guía de Mantenimiento — Actas GLPI

Estructura, responsabilidades y puntos críticos del sistema para tareas de mantenimiento y evolución. El código fuente es la única fuente de verdad.

---

## 1. Estructura de carpetas

```
actas-glpi/
├── backend/
│   ├── pom.xml                      # Dependencias y empaquetado (jar)
│   └── src/main/
│       ├── java/com/empresa/actas/
│       │   ├── ActasApplication.java        # Punto de entrada (Spring Boot)
│       │   ├── config/
│       │   │   ├── AppConfig.java           # Carga .env y crea directorio generados
│       │   │   └── CorsConfig.java          # CORS configurable (CORS_ALLOWED_ORIGINS)
│       │   ├── controller/                  # Endpoints REST (5 controllers)
│       │   ├── dto/request/                 # DTOs de entrada (validados con Jakarta)
│       │   ├── dto/response/                # DTOs de salida
│       │   ├── exception/GlobalExceptionHandler.java
│       │   └── service/                     # Lógica de negocio e integraciones
│       └── resources/
│           ├── application.yml              # Configuración central
│           └── plantillas/                  # Templates DOCX (acta entrega, checklist, devolución, formateo)
├── frontend/
│   ├── css/ (styles.css, output.css, app.css)
│   ├── js/  (config.js, ui.js, autocomplete.js, app.js, devolucion.js, formateo.js)
│   ├── pages/ (acta-entrega.html, acta-devolucion.html, acta-formateo.html)
│   ├── img/logo.png
│   └── package.json
├── docs/                            # Manual de usuario, mantenimiento, guía desarrollador
├── .env.example                     # Plantilla de variables de entorno
└── .gitignore
```

## 2. Responsabilidades por capa

| Capa / archivo | Responsabilidad |
|----------------|-----------------|
| `ActaController` | `POST /generar-acta`, `GET /descargar-acta/{zip}` |
| `DevolucionController` | `POST /generar-devolucion` |
| `FormateoSeguroController` | `POST /generar-formateo-seguro` |
| `EquipoController` | `GET /equipo/{serial}` |
| `UsuarioController` | `GET /usuarios?texto=` |
| `ActaService` | Orquesta acta + checklist → ZIP |
| `DevolucionService` | Orquesta devolución → ZIP |
| `FormateoSeguroService` | Orquesta formateo seguro → ZIP |
| `DocumentoWordService` | Prepara datos y genera los 4 DOCX (3 actas + checklist) |
| `DocxTemplateEngine` | Reemplaza `{{ vars }}` en DOCX (a nivel de run) |
| `EquipoService` | Búsqueda de equipos en GLPI (campos 5/23/4/40/17) |
| `UsuarioService` | Búsqueda de usuarios en GLPI multi-término (campos 9/34/1 + id 2) |
| `GlpiClient` | Autenticación, HttpClient y timeouts compartidos para GLPI |
| `ZipService` | Empaqueta DOCX en ZIP |
| `AppConfig` | Carga `.env` (raíz) como System properties |
| `CorsConfig` | Habilita CORS con origen configurable |
| `app.js` | Lógica de la página de entrega |
| `devolucion.js` | Lógica de la página de devolución |
| `formateo.js` | Lógica de la página de formateo seguro (máx. 4 equipos, GB) |
| `autocomplete.js` | Autocompletado de usuarios (nombre + login) |
| `config.js` | `API_URL` (único lugar con la URL del backend) |

## 3. Flujos principales

### 3.1 Generación documental

```
Frontend → POST /generar-acta (o /generar-devolucion, /generar-formateo-seguro)
  → Controller valida @Valid
  → Service convierte DTOs a Map
  → DocumentoWordService genera DOCX (template + vars)
  → ZipService crea ZIP
  → respuesta { success, nombre_zip }
  → Frontend GET /descargar-acta/{nombre_zip}
```

### 3.2 Búsqueda de equipos

```
Frontend → GET /equipo/{serial}
  → EquipoService.buscarEquipo(serial)
  → GlpiClient.iniciarSesion() → /initSession
  → GlpiClient.search("Computer", query)
  → Abreviar CPU + concatenar modelo
  → EquipoResponse { marca, tipo, modelo }
```

### 3.3 Búsqueda de usuarios

```
Frontend → GET /usuarios?texto=
  → UsuarioService.buscarUsuarios(texto)   (mín. 3 caracteres)
  → GlpiClient.iniciarSesion() + search("User", query multi-término)
  → List<UsuarioResponse> { id, nombreCompleto, login }  (máx. 10)
```

La búsqueda es multi-término: cada palabra (split por espacios) debe aparecer en firstname (9), realname (34) o login (1) — encadenados con OR dentro del término y AND entre términos.

## 4. Variables de configuración

### Variables de entorno (`.env` en la raíz o variables del sistema)

| Variable | Obligatoria | Uso |
|----------|-------------|-----|
| `GLPI_URL` | No (fallback en yml) | URL base de la API de GLPI |
| `GLPI_APP_TOKEN` | **Sí** | App-Token GLPI |
| `GLPI_USER_TOKEN` | **Sí** | User-Token GLPI |
| `CORS_ALLOWED_ORIGINS` | No (fallback en yml) | Orígenes CORS del despliegue |

> Si `GLPI_APP_TOKEN` o `GLPI_USER_TOKEN` no están definidas, la aplicación **no arranca**. Las variables del sistema tienen prioridad sobre `.env` (`AppConfig.setEnvIfMissing`).

### Propiedades de Spring (`application.yml`)

| Propiedad | Valor por defecto | Descripción |
|-----------|-------------------|-------------|
| `server.port` | `8001` | Puerto del backend (debe coincidir con `API_URL`) |
| `glpi.url` | `http://10.86.1.33/glpi/apirest.php` | Instancia GLPI |
| `app.generated-dir` | `java.io.tmpdir/actas_glpi_generados` | Directorio de DOCX/ZIP generados |
| `app.templates-dir` | `classpath:plantillas` | Origen de las plantillas DOCX |
| `app.cors.allowed-origins` | lista local | Orígenes CORS (env `CORS_ALLOWED_ORIGINS`) |

**Para cambiar el directorio de generados:** pasar la propiedad `app.generated-dir` (ej. `-Dapp.generated-dir=/ruta` al arrancar, o variable de entorno `APP_GENERATEDDIR`). En Docker usar un volumen persistente.

## 5. Dependencias externas

| Dependencia | Versión | Se usa para |
|-------------|---------|-------------|
| Java | 21 | Compilar y ejecutar el backend |
| Maven | 3.8+ | Build y arranque del backend |
| Spring Boot | 3.4.1 | Framework del backend |
| Apache POI | 5.2.5 | Lectura/escritura de DOCX |
| dotenv-java | 3.2.0 | Carga de `.env` |
| GLPI (instancia) | — | API REST de equipos y usuarios |
| Node.js | 18+ | Build de estilos y FlyonUI (frontend) |
| Tailwind CSS | 4.3.3 | Generación de `output.css` |
| FlyonUI | 2.4.1 | Componentes UI del frontend |
| Flatpickr | CDN | Selector de fechas |

## 6. Puntos críticos

1. **Tokens GLPI**: si cambian los tokens, actualizar `.env` (no está en git) o las variables del sistema. Sin tokens válidos el backend no inicia.
2. **Puerto 8001**: el frontend resuelve la URL del backend en `config.js` (`API_URL`): usa `window.API_URL` si existe, si no `http://{hostname}:8001`. Cambiar de puerto implica actualizar `window.API_URL` o el despliegue.
3. **Templates DOCX**: el nombre de cada template está fijo en `DocumentoWordService`. Si se renombra o mueve una plantilla en `resources/plantillas/`, debe actualizarse la constante correspondiente. El nombre heredado `Acta de Entrega 2 2 - copia.docx` NO debe cambiarse sin actualizar el código.
4. **Placeholders**: los templates usan `{{ var }}`. Si se agrega un placeholder nuevo, debe rellenarse en `DocumentoWordService`; de lo contrario quedará el texto literal en el DOCX.
5. **Límites**: el frontend limita (3 equipos entrega/devolución, 4 formateo; 9 hardware entrega, 3 otros devolución); el backend rellena hasta los límites del template (10 equipos, 11 hardware, 10 otros, 4 formateo). Ajustes de límite deben revisarse en ambos lados.
6. **Formato de fecha**: el backend espera `YYYY-MM-DD` (Jackson) y el frontend la envía así internamente aunque el usuario la vea como DD-MM-YYYY.
7. **GLPI caído / sin red**: `EquipoService` y `UsuarioService` devuelven resultados vacíos (no rompen el flujo) **pero sin registrar log** — un fallo de GLPI es invisible en `docker logs`. Antes del despliegue conviene agregar `log.error` en los `catch`.
8. **Timeouts GLPI**: 10 s de conexión y 30 s por request (`GlpiClient`). Ajustar `CONNECT_TIMEOUT`/`REQUEST_TIMEOUT` si la red de GLPI es lenta.
9. **`node_modules`**: no está versionado; tras un clon nuevo hay que ejecutar `npm install` en `frontend/` para que `flyonui.js` funcione.
10. **CORS**: si el frontend se sirve desde otro origen/puerto, definir `CORS_ALLOWED_ORIGINS` (no editar la lista dura). Lista por defecto: `127.0.0.1`, `localhost`, puertos `5500`, `5501`, `8080` y `localhost:8001`.
11. **Path traversal en descarga**: el nombre del ZIP se valida en `ActaController.esNombreZipInvalido` (rechaza `..`, `/`, `\`) y se normaliza contra `app.generated-dir`. No debilitar esta defensa.

## 7. Regenerar estilos Tailwind

Editar `frontend/css/app.css` (fuente) y compilar:

```bash
cd frontend
npm run build:css
```

Resultado: `frontend/css/output.css` (versionado). Revisar el diff antes de confirmar: el comando puede purgar clases no usadas en las páginas actuales.

## 8. Comandos útiles

| Tarea | Comando |
|-------|---------|
| Compilar backend | `cd backend && mvn compile` |
| Empaquetar backend | `cd backend && mvn clean package -DskipTests` |
| Arrancar backend | `cd backend && mvn spring-boot:run` |
| Instalar dependencias frontend | `cd frontend && npm install` |
| Regenerar CSS | `cd frontend && npm run build:css` |
| Verificar sintaxis JS | `node --check frontend/js/*.js` |

## 9. Consideraciones de despliegue

- No hay `Dockerfile` ni `docker-compose.yml` en el repositorio; `docker.md` contiene la propuesta (imagen backend con `eclipse-temurin:21-jre`, frontend con `nginx:alpine`, compose con `env_file: .env`, puertos `8001`/`80`).
- El backend genera archivos en `app.generated-dir`; en un despliegue, definir un **directorio persistente** (no el temporal del sistema) para no perder actas al reiniciar.
- Las plantillas DOCX viajan dentro del JAR (`classpath:plantillas`). Para personalizarlas sin recompilar, cambiar `app.templates-dir` a una ruta externa con los mismos nombres de archivo.
- Los tokens no deben quedar en el código ni en git; usar variables de entorno del servidor (tienen prioridad sobre `.env`).
- Definir `CORS_ALLOWED_ORIGINS` con el host real del frontend.
- Considerar persistencia de `app.generated-dir` y limpieza periódica (el sistema no borra ZIP envejecidos).