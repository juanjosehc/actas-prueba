# Manual de Usuario — Actas GLPI

Sistema para generar actas de **entrega**, **devolución** y **formateo seguro** de activos tecnológicos. Los documentos resultantes son archivos Word (DOCX) empaquetados en ZIP que se descargan automáticamente.

---

## 1. Acceso

1. Asegurarse de que el **backend** esté corriendo (puerto `8001`).
2. Abrir en el navegador las páginas del frontend, por ejemplo con Live Server de VS Code:
   - `http://127.0.0.1:5501/frontend/pages/acta-entrega.html`
3. Usar la barra de navegación para cambiar entre **Acta de Entrega**, **Acta de Devolución** y **Acta de Formateo Seguro**.

> `index.html` redirige automáticamente a `pages/acta-entrega.html`.

## 2. Navegación

La barra superior muestra el logo de Coltefinanciera y **tres** enlaces:

- **Acta de Entrega**
- **Acta de Devolución**
- **Acta de Formateo Seguro**

El enlace de la página actual aparece resaltado.

---

## 3. Acta de Entrega

Genera **2 documentos**: el acta de entrega y la lista de chequeo.

### 3.1 Datos del acta

| Campo | Descripción |
|-------|-------------|
| Fecha | DD-MM-YYYY (selector de fechas) |
| Entregado a | Persona que recibe (con autocompletado) |
| Cargo quien recibe | Cargo de la persona que recibe |
| Entregado por | Persona que entrega (con autocompletado) |
| Cargo quien entrega | Cargo de la persona que entrega |
| Asunto | Motivo/contexto del acta |
| Número SAC | Número SAC asociado |
| Sistema operativo | Windows 10, Windows 11 o Mac OS (obligatorio) |
| Observaciones | Texto libre (opcional) |

### 3.2 Equipos (máx. 3)

Cada equipo tiene: **Serial**, **Marca**, **Tipo**, **Modelo**, **Inventario**.

- Escribir el serial y hacer click en **Buscar**. Si el equipo existe en GLPI, se rellenan automáticamente *Marca*, *Tipo* y *Modelo* (estos campos quedan deshabilitados).
- Serial e Inventario son obligatorios.
- Botones **Añadir Equipo** / **Eliminar** para agregar o quitar equipos.

### 3.3 Hardware / Software (máx. 9)

Cada registro tiene: **Tipo** (ej. Monitor, Teclado), **Descripción** y **Programa**.

### 3.4 Lista de chequeo (Checklist)

- 36 verificaciones organizadas en 6 secciones desplegables.
- Se puede marcar o desmarcar todo por sección.
- El resultado de cada checkbox se refleja en el documento como una "X" marcada o vacía.

### 3.5 Generar acta

1. Click en **Generar Acta**.
2. Si faltan campos, se muestran en rojo y la página se desplaza al primer error.
3. Al completarse, se descarga automáticamente el ZIP `ActaLista_{serial}_{asunto}.zip`.

> El ZIP contiene el acta (`.docx`) y la lista de chequeo (`.docx`).

---

## 4. Acta de Devolución

Genera **1 documento**: el acta de devolución.

### 4.1 Datos del acta

| Campo | Descripción |
|-------|-------------|
| Fecha | DD-MM-YYYY (selector de fechas) |
| Nombre quien entrega | Con autocompletado |
| Cédula quien entrega | Número de cédula |
| Cargo quien entrega | Cargo de quien entrega |
| Recibido por | Con autocompletado |
| Cargo quien recibe | Cargo de quien recibe |
| Área quien recibe | Área de quien recibe |
| Motivo devolución | Razón de la devolución |
| Observaciones | Texto libre (opcional) |

### 4.2 Equipos (máx. 3)

Igual que en entrega, pero cada equipo incluye además el campo **Estado** (obligatorio).

### 4.3 Otros Elementos (máx. 3)

Solo se solicita el **Tipo** de cada elemento adicional (ej. Teclado, Mouse, Cargador).

### 4.4 Generar acta

1. Click en **Generar Acta Devolución**.
2. Si faltan campos, se muestran en rojo y se desplaza al primer error.
3. Se descarga automáticamente el ZIP `Devolucion_{serial}_{motivo}.zip`.

> La devolución NO incluye checklist ni sistema operativo.

---

## 5. Acta de Formateo Seguro

Genera **1 documento**: el acta de formateo seguro. Límite de **4 equipos**.

### 5.1 Datos del acta

| Campo | Descripción |
|-------|-------------|
| Fecha | DD-MM-YYYY (selector de fechas) |
| Entregado a | Con autocompletado |
| Cargo quien recibe | Cargo de quien recibe |
| Entregado por | Con autocompletado |
| Cargo quien entrega | Cargo de quien entrega |
| Asunto | Motivo del formateo |

### 5.2 Equipos (máx. 4)

Cada equipo tiene: **Serial**, **Marca**, **Tipo**, **Modelo**, **Inventario** y **Cantidad en GB**.

- Buscar por serial rellena *Marca*, *Tipo* y *Modelo* desde GLPI.
- **Serial**, **Inventario** y **Cantidad en GB** son obligatorios.
- Límite mínimo 1 equipo y máximo **4**.

### 5.3 Generar acta

1. Click en **Generar Acta de Formateo Seguro**.
2. Si faltan campos, se muestran en rojo y se desplaza al primer error.
3. Se descarga automáticamente el ZIP `FormateoSeguro_{serial}_{asunto}.zip`.

> El formateo seguro NO incluye checklist, sistema operativo ni hardware/otros.

---

## 6. Autocompletado de usuarios

Al escribir **3 o más caracteres** en los campos de personas (Entregado a, Entregado por, Recibido por), se muestran sugerencias de usuarios de GLPI.

- Cada sugerencia muestra el **nombre completo** y, debajo, el **login** de la cuenta.
- Navegar con las flechas **↑ / ↓**.
- Seleccionar con **Enter**.
- Cerrar la lista con **Esc** o haciendo click fuera.

La búsqueda funciona aunque el término no sea consecutivo ni esté en el mismo campo: por ejemplo, escribir **"Julian Celis"** encuentra a una persona con nombres "Julian Alejandro" y apellidos "Celis Valderrama".

## 7. Descarga de documentos

Los archivos se descargan automáticamente. El ZIP contiene:

| Tipo de acta | Contenido del ZIP |
|--------------|-------------------|
| Entrega | Acta de entrega + Lista de chequeo |
| Devolución | Acta de devolución |
| Formateo seguro | Acta de formateo seguro |

Los DOCX pueden abrirse con Word, LibreOffice o cualquier editor compatible.

---

## 8. Errores comunes

| Situación | Qué ocurre |
|-----------|------------|
| Backend detenido | Los mensajes de error aparecen en rojo al generar o buscar |
| "Complete los campos obligatorios" | Hay campos en rojo; corregir y volver a intentar |
| "Debe seleccionar un sistema operativo" | Falta elegir SO (solo entrega) |
| "Debe completar Serial o Inventario" | Faltan datos obligatorios de un equipo |
| "Debe completar Cantidad en GB..." | Falta el GB (solo formateo seguro) |
| "Se alcanzó el máximo permitido..." | Se excedió el límite de equipos o hardware/otros |
| Límites | Entrega y devolución: 3 equipos, entrega 9 hardware, devolución 3 otros. Formateo: 4 equipos |
| Equipo no encontrado en GLPI | Marca/Tipo/Modelo quedan vacíos; se puede completar manualmente |
| Sin sugerencias en autocompletado | Verificar texto de 3+ caracteres o conectividad con GLPI |
| "Error generando la documentación" | El backend no pudo generar el DOCX; revisar que los tokens GLPI y el backend estén operativos