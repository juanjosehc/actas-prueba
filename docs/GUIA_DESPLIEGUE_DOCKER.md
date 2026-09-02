# Guía de Despliegue Docker — Actas GLPI

Runbook para el primer despliegue del sistema en un servidor interno usando Docker Compose. El objetivo es servir el frontend web (nginx) y el backend API (Spring Boot) en el mismo servidor, con el backend conectado a la instancia GLPI de la organización.

> **Estado actual del repositorio:** no hay `Dockerfile` ni `docker-compose.yml` versionados. El archivo `docker.md` de la raíz contiene la propuesta original. Esta guía transforma esa propuesta en archivos listos para usar, con las correcciones necesarias (persistencia de documentos, `flyonui`, CORS).

---

## 1. Arquitectura desplegada

```
┌─────────────┐   HTTP :80    ┌──────────────────┐
│  Navegador  │ ────────────> │  nginx (frontend)│
│  (usuario)  │               │  actas-frontend  │
└─────────────┘               └──────────────────┘
        │ API  http://{host}:8001
        ▼
┌──────────────────┐   HTTPS   ┌──────────────────────┐
│ Spring Boot      │ ────────> │  GLPI API REST       │
│ actas-backend    │  initSession + search           │
└──────────────────┘           └──────────────────────┘
        │ escribe DOCX/ZIP
        ▼
┌──────────────────┐
│ /actas_generados │  volumen persistente
└──────────────────┘
```

- **Frontend (nginx, puerto 80):** sirve las páginas estáticas. El navegador resuelve la URL del backend con `frontend/js/config.js`: usa `window.API_URL` si existe; si no, `http://{hostname}:8001`. Por eso el backend **debe** quedar expuesto en el puerto 8001 **del mismo host** que el frontend.
- **Backend (Spring Boot, puerto 8001):** API REST. Consulta GLPI, genera los DOCX desde las plantillas y los empaqueta en ZIP. Los ZIP se guardan en el directorio `app.generated-dir` (en el contenedor se fija a `/actas_generados`, persistente por volumen).
- **GLPI:** externo, debe ser alcanzable desde el contenedor del backend.

---

## 2. Requisitos previos

En el servidor donde se desplegará:

| Requisito | Detalle |
|-----------|---------|
| Docker + Docker Compose | Compose v2 (incluido en Docker Desktop / plugin de Docker Engine) |
| Maven 3.8+ | Solo para construir el JAR del backend en el host (o usar un contenedor de build) |
| Java 21 (JDK) | Solo para ejecutar `mvn package` en el host |
| Acceso a GLPI | Los tokens GLPI deben ser válidos y la URL alcanzable desde el contenedor |
| `.env` | Crear en la raíz del proyecto copiando `.env.example` y completando tokens (ver sección 6) |

Los puertos libres en el servidor: **8001** (backend) y **80** (frontend).

> No hace falta Node.js en el host para el frontend: el `Dockerfile` de nginx instala `flyonui` dentro de una etapa de build (sección 4).

---

## 3. Archivos a crear

Ubicación en el repositorio:

```
actas-glpi/
├── .env                          # ← crear (no está en git)
├── docker-compose.yml            # ← crear
├── backend/
│   ├── Dockerfile                # ← crear
│   └── .dockerignore             # ← crear (recomendado)
└── frontend/
    ├── Dockerfile                # ← crear
    └── .dockerignore             # ← crear
```

Los cuatro archivos se detallan en las secciones siguientes.

---

## 4. Backend — `backend/Dockerfile`

```dockerfile
# Imagen de solo ejecución (JRE). No necesita JDK ni Maven dentro del contenedor.
FROM eclipse-temurin:21-jre

# Directorio de trabajo. Importante: AppConfig busca ../.env relativo al CWD;
# en el contenedor apunta a / (no existe -> es inofensivo, ver sección 6).
WORKDIR /app

# El JAR se empaqueta en el host con `mvn clean package`.
# El nombre corresponde a artifactId-version del pom.xml (actas-glpi-1.0.0).
COPY target/actas-glpi-1.0.0.jar app.jar

EXPOSE 8001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Puntos a conocer:

- **Nombre del JAR:** viene de `backend/pom.xml` (`<artifactId>actas-glpi</artifactId>` + `<version>1.0.0</version>`). Si se cambia la versión en el `pom.xml`, hay que actualizar el `COPY` y reconstruir la imagen.
- **Variables de entorno:** el contenedor recibe `GLPI_URL`, `GLPI_APP_TOKEN`, `GLPI_USER_TOKEN` y `CORS_ALLOWED_ORIGINS` por `env_file` en el compose (sección 6). En `AppConfig`, las variables del sistema tienen prioridad sobre `.env`, así que el backend usa las del contenedor. El intento de leer `../.env` (que en el contenedor apunta a `/`) falla silenciosamente y no afecta nada.
- **Directorio de generados:** por defecto `app.generated-dir` es `${java.io.tmpdir}/actas_glpi_generados` (en el contenedor, `/tmp/...`). **Ese directorio se pierde al reiniciar el contenedor** si no se cambia. El compose fija `APP_GENERATEDDIR=/actas_generados` con un volumen persistente (sección 5).
- **Puerto:** `server.port=8001` viene de `application.yml`; el `EXPOSE` es informativo y los puertos se publican en el compose.

### `backend/.dockerignore` (recomendado)

Evita mandar el `target/` completo (pesado) como contexto de build:

```
target/
.git
```

---

## 5. Frontend — `frontend/Dockerfile`

```dockerfile
# Etapa 1: instalar flyonui. node_modules NO está en git; hay que generarlo.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install

# Etapa 2: servir las páginas con nginx.
FROM nginx:alpine
COPY . /usr/share/nginx/html
COPY --from=build /app/node_modules/flyonui /usr/share/nginx/html/node_modules/flyonui
```

Por qué esta estructura:

- **`flyonui.js` se carga desde `node_modules`.** Las páginas (`pages/acta-*.html`) incluyen `<script src="../node_modules/flyonui/flyonui.js">`, así que el navegador pide `/node_modules/flyonui/flyonui.js`. Como `node_modules` está en `.gitignore` (no viaja en el repositorio), el `Dockerfile` lo instala en una etapa de build y lo copia al árbol servido por nginx.
- **`COPY . /usr/share/nginx/html`** copia `pages/`, `js/`, `css/`, `img/` e `index.html`. Las rutas relativas (`../js/...`, `../img/...`) resuelven correctamente porque nginx sirve todo el árbol en `/`.
- **No hace falta compilar Tailwind en el despliegue:** `frontend/css/output.css` ya está compilado y versionado. El CSS no se regenera en Docker (para eso está `npm run build:css` en desarrollo).
- **Sin archivo de config nginx extra:** la imagen `nginx:alpine` por defecto ya sirve `/usr/share/nginx/html` en el puerto 80 con MIME types correctos. Solo se agrega config si se quiere gzip, caché o un reverse proxy del backend (ver sección 9).

### `frontend/.dockerignore`

Mantiene la imagen liviana (sin `node_modules` del host ni `.git`):

```
node_modules/
.git/
```

---

## 6. Variables de entorno — `.env`

El compose carga el `.env` de la raíz del proyecto (el mismo que usa la app en desarrollo). Los tokens son obligatorios: sin ellos el backend **no arranca** (no tienen valor por defecto en `application.yml`).

```dotenv
# .env  (en la raíz del proyecto)
GLPI_URL=http://10.86.1.33/glpi/apirest.php
GLPI_APP_TOKEN=cambia_por_tu_app_token
GLPI_USER_TOKEN=cambia_por_tu_user_token

# Orígenes que el navegador podrá llamar. En producción DEBE incluir el host
# real del frontend (IP o nombre). Ejemplo con el servidor en 10.0.0.50:
CORS_ALLOWED_ORIGINS=http://10.0.0.50,http://10.0.0.50:80
```

**`CORS_ALLOWED_ORIGINS` es la diferencia clave con desarrollo.** En local la lista por defecto de `application.yml` cubre `localhost:5500/5501/8080`. En el servidor el origen es el host real (ej. `http://10.0.0.50`), y si no se agrega, el navegador bloqueará las llamadas al backend. Alternativa equivalente: poner la variable en el bloque `environment` del servicio `backend` en el compose (la sección siguiente lo muestra comentado).

> El `.env` está en `.gitignore`; crear la copia nueva en cada servidor con `Copy-Item .env.example .env` (PowerShell) y completar los valores. `CORS_ALLOWED_ORIGINS` no está en `.env.example`: agregarla manualmente.

---

## 7. Orquestación — `docker-compose.yml`

```yaml
services:
  backend:
    build: ./backend
    container_name: actas-backend
    restart: unless-stopped
    ports:
      - "8001:8001"
    env_file:
      - .env                 # GLPI_URL, GLPI_APP_TOKEN, GLPI_USER_TOKEN, CORS_ALLOWED_ORIGINS
    environment:
      # Spring rellena app.generated-dir desde APP_GENERATEDDIR (relaxed binding).
      # Evita java.io.tmpdir (/tmp) que se borra al reiniciar el contenedor.
      APP_GENERATEDDIR: /actas_generados
    volumes:
      - actas_generados:/actas_generados

  frontend:
    build: ./frontend
    container_name: actas-frontend
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  actas_generados:
```

Línea por línea:

| Bloque | Efecto |
|--------|--------|
| `build: ./backend` / `./frontend` | Construye cada imagen con su `Dockerfile`. Rutas relativas al compose (raíz del proyecto). |
| `ports: 8001:8001` | Expone el backend en el mismo puerto que usa `config.js` (`http://{hostname}:8001`) del frontend. |
| `env_file: .env` | Pasa las variables al contenedor como variables de entorno reales. Otra vía: definir cada una en `environment:`. |
| `APP_GENERATEDDIR` | Spring Boot (relaxed binding) mapea `APP_GENERATEDDIR` → propiedad `app.generated-dir`. |
| `volumes: actas_generados:/actas_generados` | Volumen nombrado: los ZIP/DOCX sobreviven a `docker compose restart` (no a `down -v`, que borra el volumen). |
| `restart: unless-stopped` | El contenedor se levanta solo si el servidor reinicia o Docker se cae. |
| `depends_on` | Solo orden de arranque (backend antes que frontend). No espera a que el backend responda; las llamadas del navegador son posteriores al arranque completo. |

Si se prefiere pasar CORS por el compose en lugar de `.env`:

```yaml
    environment:
      APP_GENERATEDDIR: /actas_generados
      CORS_ALLOWED_ORIGINS: "http://10.0.0.50,http://10.0.0.50:80"
```

---

## 8. Build, arranque y verificación

### 8.1 Construir y levantar

```powershell
# 1) Empaquetar el JAR del backend (debe hacerse antes de docker build)
cd backend
mvn clean package -DskipTests
cd ..

# 2) Levantar todo (compila las imágenes y arranca los contenedores)
docker compose up -d --build
```

No hace falta `npm install` en el host: lo hace la etapa de build del Dockerfile del frontend.

### 8.2 Verificar el arranque

```powershell
docker compose ps
docker logs actas-backend --tail 30
```

En los logs del backend se debe ver el banner de Spring Boot y que el servidor quedó en el puerto 8001. Si arrancó, probar la API:

```bash
# Desde el propio servidor
curl -s http://127.0.0.1:8001/equipo/ABC123XYZ
# → {"marca":"...","tipo":"...","modelo":"..."}   (con un serial existente en GLPI)
# → {"marca":"","tipo":"","modelo":""}            (serial no encontrado)

curl -s "http://127.0.0.1:8001/usuarios?texto=julian"
# → [{"id":..,"nombreCompleto":"...","login":"..."}]
```

Desde el navegador de un equipo en la red interna:

1. Abrir `http://SERVIDOR/pages/acta-entrega.html` (y las otras dos actas).
2. Buscar un serial: debe autocompletar marca/tipo/modelo.
3. Escribir un nombre (3+ caracteres) en el campo de persona: deben aparecer sugerencias.
4. Completar el formulario y generar: debe descargar el ZIP. Abrir los DOCX y confirmar que los placeholders quedaron reemplazados (sin texto literal `{{ ... }}`).

> En una máquina cliente, el firewall del servidor debe tener abiertos los puertos 80 y 8001 para el tráfico entrante.

### 8.3 Reconstruir tras cambios

```powershell
# Cambios en backend:
cd backend; mvn clean package -DskipTests; cd ..
docker compose up -d --build backend

# Cambios en frontend:
docker compose up -d --build frontend
```

---

## 9. Troubleshooting

| Síntoma | Causa probable | Qué hacer |
|---------|----------------|-----------|
| `docker compose up` falla con "env file .env not found" | `.env` no existe en la raíz | Crearlo desde `.env.example` y completar los tokens. |
| El backend no arranca | `GLPI_APP_TOKEN` / `GLPI_USER_TOKEN` vacíos o faltantes | Revisar `docker logs actas-backend`: si falta el token, la app cae al resolver `${GLPI_APP_TOKEN}`. |
| Las búsquedas de equipo/usuario devuelven vacío "sin motivo" | GLPI no alcanzable desde el contenedor, o tokens inválidos | **El backend no registra log en estos catch** (ver `EquipoService`/`UsuarioService`). Probar la conectividad con un contenedor con curl: `docker run --rm --network host curlimages/curl -s -H "App-Token: ..." -H "Authorization: user_token ..." "http://10.86.1.33/glpi/apirest.php/initSession"`. Si falla desde el contenedor pero OK desde el host, revisar ruteo/DNS/firewall de Docker. |
| Consola del navegador: error CORS | El origen del frontend no está en `CORS_ALLOWED_ORIGINS` | Poner el host exacto (con/sin puerto) en `.env` o en `environment:` del compose y reconstruir. |
| La página carga pero "Error interno" al buscar | Llamada al backend bloqueada o backend caído | `docker compose ps`; probar el mismo `GET /equipo/...` con curl. |
| El ZIP se genera pero la descarga dice "Archivo no encontrado" | `app.generated-dir` apunta a un path sin volumen (se perdió al reiniciar) | Verificar que `APP_GENERATEDDIR` está fijado y que la ruta coincide. |
| Las actas anteriores desaparecen al reiniciar el contenedor | Directorio temporal sin volumen | Usar el volumen `actas_generados` del compose (no eliminar con `down -v`). |
| Puerto 80 ocupado en el servidor | Otro proceso usa 80 | Cambiar el mapeo a `"8080:80"` y abrir la página por `http://SERVIDOR:8080`; el origen CORS cambia en consecuencia. |
| `docker compose` no levanta la red interna | Proxy/DNS corporativo bloquea descargas de imágenes | Configurar proxy de Docker (`~/.docker/config.json`) para bajar `eclipse-temurin`/`nginx`/`node`. |

---

## 10. Seguridad y notas para producción

- **Sin autenticación en la API.** Los endpoints (`/generar-acta`, `/descargar-acta/{zip}`, `/equipo/{serial}`, `/usuarios`) están abiertos a cualquier cliente que alcance el puerto 8001. En el despliegue interno inicial basta con restringir el puerto 8001 por firewall a la red de TI; no exponerlo a Internet.
- **Sin HTTPS.** Para un acceso externo futuro, poner el frontend detrás de un reverse proxy con TLS (nginx/caddy) y actualizar `CORS_ALLOWED_ORIGINS` al dominio `https://...`. El backend puede quedar en una red interna solo accesible por el proxy y el frontend.
- **Tokens GLPI** viajan en `.env` (no está en git) o como secretos del orquestador. No se deben escribir en el código ni en los `Dockerfile`.
- **Volumen de actas crece sin límite.** El sistema no elimina ZIP/DOCX antiguos. Programar una limpieza periódica del volumen `actas_generados` (delete de archivos con más de N días) según la política de la organización.
- **Timeouts GLPI:** 10 s de conexión y 30 s por request (`GlpiClient`). Si la red hacia GLPI es lenta, los buscadores pueden devolver vacío en lugar de esperar; verificar antes de escalar a red pública.
- **Plantillas dentro del JAR.** `app.templates-dir=classpath:plantillas`; las plantillas viajan compiladas en el JAR. Para personalizarlas sin recompilar, montar una carpeta en el contenedor y apuntar `APP_TEMPLATESDIR` (relaxed binding de `app.templates-dir`) a esa ruta con los mismos nombres de archivo (ver `docs/GUIA_EDITAR_PLANTILLAS.md`).