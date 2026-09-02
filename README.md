# Actas GLPI

Sistema para la generación automatizada de documentos Word (DOCX) de actas de **entrega**, **devolución** y **formateo seguro** de activos tecnológicos, integrado con GLPI para la consulta de equipos y usuarios.

## Descripción

El sistema permite al personal de TI generar actas oficiales y listas de chequeo a partir de datos capturados en un formulario web. Los documentos se generan en formato DOCX, se empaquetan en ZIP y se descargan automáticamente al navegador.

El frontend es una aplicación web estática (HTML/CSS/JS vanilla con Tailwind CSS y FlyonUI). El backend es una API REST en Java 21 + Spring Boot 3.4.1 que consulta GLPI, genera los DOCX desde plantillas Word y los empaqueta en ZIP.

### Tipos de documento generados

| Tipo | ZIP | Contenido |
|------|-----|-----------|
| **Acta de Entrega** | `ActaLista_{serial}_{asunto}.zip` | Acta de entrega + Lista de chequeo (2 DOCX) |
| **Acta de Devolución** | `Devolucion_{serial}_{motivo}.zip` | Acta de devolución (1 DOCX) |
| **Acta de Formateo Seguro** | `FormateoSeguro_{serial}_{asunto}.zip` | Acta de formateo seguro (1 DOCX, máx. 4 equipos) |

## Requisitos previos

- **Java 21** (JDK).
- **Maven 3.8+** (para el backend).
- **Node.js 18+** (opcional, para regenerar estilos Tailwind; necesaria para `npm install` de FlyonUI, ver [Frontend](#4-frontend)).
- Cuenta en una instancia **GLPI** con permisos de API REST (App-Token + User-Token).

## Estructura del proyecto

```
actas-glpi/
├── backend/                     # API REST (Spring Boot 3.4.1, Java 21)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/empresa/actas/
│       │   ├── ActasApplication.java
│       │   ├── config/          # AppConfig (.env), CorsConfig
│       │   ├── controller/      # Acta, Devolucion, FormateoSeguro, Equipo, Usuario
│       │   ├── dto/             # Request / Response DTOs
│       │   ├── exception/       # GlobalExceptionHandler
│       │   └── service/         # Acta, Devolucion, FormateoSeguro, DocumentoWord,
│       │                        # DocxTemplateEngine, Equipo, Usuario, GlpiClient, Zip
│       └── resources/
│           ├── application.yml
│           └── plantillas/      # Templates DOCX (acta entrega, checklist, devolución, formateo)
├── frontend/                    # Interfaz web (HTML/CSS/JS)
│   ├── css/                     # styles.css, output.css (Tailwind), app.css (fuente)
│   ├── js/                      # config.js, ui.js, autocomplete.js, app.js, devolucion.js, formateo.js
│   ├── pages/                   # acta-entrega.html, acta-devolucion.html, acta-formateo.html
│   ├── img/                     # logo.png
│   └── package.json             # Tailwind CSS + FlyonUI
├── docs/
│   ├── MANUAL_USUARIO.md            # Manual de usuario
│   ├── MANTENIMIENTO.md             # Guía de mantenimiento
│   ├── GUIA_DESARROLLADOR.md        # Guía de onboarding para nuevos desarrolladores
│   ├── GUIA_DESPLIEGUE_DOCKER.md    # Runbook de despliegue Docker
│   ├── GUIA_PRUEBAS_MANUALES.md     # Checklist de QA y pruebas manuales
│   └── GUIA_EDITAR_PLANTILLAS.md    # Cómo editar plantillas DOCX
├── .env.example                 # Plantilla de variables de entorno
├── ARQUITECTURA.md              # Documentación de arquitectura
├── DOCUMENTACION_TECNICA.md     # Documentación técnica detallada
├── FLUJO_FUNCIONAL.md           # Flujo funcional por tipo de acta
└── docker.md                    # Notas de despliegue Docker (propuesta)
```

## Instalación

### 1. Backend

```bash
cd backend
mvn clean package -DskipTests
```

### 2. Variables de entorno

Copiar `.env.example` a `.env` en la **raíz del proyecto** (no dentro de `backend/`) y completar los valores:

```
GLPI_URL=http://10.86.1.33/glpi/apirest.php
GLPI_APP_TOKEN=tu-app-token
GLPI_USER_TOKEN=tu-user-token
```

- `GLPI_APP_TOKEN` y `GLPI_USER_TOKEN` son **obligatorios**: si no están definidos, la aplicación **no arranca** (no hay valor por defecto).
- `GLPI_URL` tiene un valor por defecto; puede sobrescribirse con la variable de entorno del sistema (esta tiene prioridad sobre `.env`).
- El archivo `.env` está excluido de git (`.gitignore`).

### 3. Ejecutar el backend

```bash
cd backend
mvn spring-boot:run
```

El servidor arranca en `http://127.0.0.1:8001`. El frontend construye la URL del backend desde el host de la página (ver `frontend/js/config.js`); en desarrollo con Live Server, `API_URL` resuelve a `http://127.0.0.1:8001`.

### 4. Frontend

Instalar dependencias (FlyonUI y Tailwind):

```bash
cd frontend
npm install
```

Abrir las páginas en el navegador:

```
frontend/pages/acta-entrega.html
frontend/pages/acta-devolucion.html
frontend/pages/acta-formateo.html
```

O usar Live Server de VS Code (puerto configurado: **5501**). El CORS del backend ya permite los orígenes locales por defecto; en producción se configura con `CORS_ALLOWED_ORIGINS`.

> **Nota:** `flyonui.js` se carga directamente desde `node_modules`, por lo que `npm install` es obligatorio para que los componentes FlyonUI funcionen. El CSS compilado (`output.css`) sí está versionado y funciona sin build.

### 5. Regenerar los estilos Tailwind (opcional)

Los cambios de estilos se hacen en `frontend/css/app.css` y se compilan con:

```bash
cd frontend
npm run build:css
```

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/generar-acta` | Genera acta de entrega + lista de chequeo (DOCX → ZIP) |
| `POST` | `/generar-devolucion` | Genera acta de devolución (DOCX → ZIP) |
| `POST` | `/generar-formateo-seguro` | Genera acta de formateo seguro (DOCX → ZIP) |
| `GET` | `/descargar-acta/{nombreZip}` | Descarga el ZIP generado |
| `GET` | `/equipo/{serial}` | Consulta un equipo en GLPI por serial (marca, tipo, modelo) |
| `GET` | `/usuarios?texto=...` | Busca usuarios en GLPI (autocompletado: nombre + login) |

## Tecnologías

**Backend:**
- Java 21, Spring Boot 3.4.1
- Apache POI 5.2.5 (manipulación DOCX)
- Jackson (JSON), Lombok, Jakarta Validation
- dotenv-java 3.2.0 (carga de `.env`)

**Frontend:**
- HTML5, CSS3, JavaScript vanilla
- Tailwind CSS 4.3.3, FlyonUI 2.4.1
- Flatpickr (selector de fechas)

## Notas importantes

- La URL del backend se resuelve en `frontend/js/config.js` (`API_URL`): usa `window.API_URL` si existe, si no `http://{hostname}:8001`. El backend **debe** ejecutarse en el puerto 8001.
- Los DOCX generados se guardan en `app.generated-dir` (por defecto `java.io.tmpdir/actas_glpi_generados`); puede sobrescribirse con la propiedad de Spring `app.generated-dir`.
- No se generan documentos PDF: solo DOCX empaquetados en ZIP.
- La arquitectura, los flujos funcionales, la documentación técnica, el manual de usuario, la guía de mantenimiento y la guía para desarrolladores están en los archivos `*​.md` de la raíz y `docs/`.

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Capas, stack, integración GLPI, generación de documentos |
| [DOCUMENTACION_TECNICA.md](DOCUMENTACION_TECNICA.md) | Especificación detallada de endpoints, DTOs, motor DOCX, ZIP, seguridad |
| [FLUJO_FUNCIONAL.md](FLUJO_FUNCIONAL.md) | Paso a paso de cada acta, GLPI y generación |
| [docs/MANUAL_USUARIO.md](docs/MANUAL_USUARIO.md) | Manual de usuario (las 3 actas) |
| [docs/GUIA_DESARROLLADOR.md](docs/GUIA_DESARROLLADOR.md) | Onboarding: dónde empezar, request flow, lugar de generación |
| [docs/MANTENIMIENTO.md](docs/MANTENIMIENTO.md) | Mantenimiento y evolución |
| [docs/GUIA_DESPLIEGUE_DOCKER.md](docs/GUIA_DESPLIEGUE_DOCKER.md) | Despliegue Docker: Dockerfiles, compose, verificación y troubleshooting |
| [docs/GUIA_PRUEBAS_MANUALES.md](docs/GUIA_PRUEBAS_MANUALES.md) | QA: casos positivos/negativos por acta, curls reales, verificación DOCX |
| [docs/GUIA_EDITAR_PLANTILLAS.md](docs/GUIA_EDITAR_PLANTILLAS.md) | Edición de plantillas Word, placeholders y límites |
| [docker.md](docker.md) | Notas de despliegue Docker