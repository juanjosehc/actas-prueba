/*
====================================================
ACTA DE FORMATEO SEGURO - FRONTEND
====================================================

Responsabilidades:

- Gestión de datos del acta de formateo seguro.
- Administración dinámica de equipos (agregar/eliminar/buscar).
- Validaciones de formulario y equipos.
- Construcción del payload para el backend.
- Descarga automática del ZIP generado.

Diferencias con entrega/devolución:

- Incluye campo "GB" por cada equipo (obligatorio).
- No incluye checklist, sistema operativo ni hardware.
- Máximo 4 equipos (capacidad de la plantilla DOCX).
Endpoints utilizados:

- GET  /equipo/{serial}          → Consulta equipo en GLPI por serial.
- POST /generar-formateo-seguro  → Genera acta de formateo seguro (DOCX).
- GET  /descargar-acta/{archivo} → Descarga el ZIP generado.

Flujo principal:

1. Usuario completa campos obligatorios.
2. Click en "Generar Acta de Formateo Seguro" ejecuta generarFormateoSeguro().
3. Se validan campos y equipos (serial, inventario, GB).
4. Se construye el payload completo.
5. Se envía POST al backend.
6. Se recibe nombre del ZIP y se descarga automáticamente.

====================================================
*/

/*
----------------------------------------------------
LÍMITES DE REGISTROS (capacidad de plantillas DOCX)
----------------------------------------------------
*/
const MAX_EQUIPOS = 4;
const MSG_MAX_EQUIPOS = "Se alcanzó el máximo permitido de 4 equipos.";

/**
 * Genera el acta de formateo seguro.
 *
 * Flujo:
 * 1. Validar campos obligatorios (fecha, entregado_a, cargo_recibe, etc.).
 * 2. Validar que cada equipo tenga serial, inventario y GB.
 * 3. Construir objetos de equipos.
 * 4. Armar el payload completo.
 * 5. Enviar POST a /generar-formateo-seguro.
 * 6. Descargar el ZIP resultante vía /descargar-acta.
 */
async function generarFormateoSeguro() {

    try {

        const camposObligatorios = [

            "fecha",
            "entregado_a",
            "cargo_recibe",
            "entregado_por",
            "cargo_entrega",
            "asunto"

        ];

        let primerCampoInvalido = null;

        camposObligatorios.forEach(id => {

            const valido = validarCampo(id);

            if (!valido && !primerCampoInvalido) {

                primerCampoInvalido =
                    document.getElementById(id);

            }

        });

        const errorEquipo = validarEquipos();

        if (primerCampoInvalido) {

            primerCampoInvalido.scrollIntoView({
                behavior: "smooth",
                block: "center"
            });

            setTimeout(() => {
                primerCampoInvalido.focus();
            }, 300);

            return;
        }

        if (errorEquipo) {

            errorEquipo.elemento.scrollIntoView({
                behavior: "smooth",
                block: "center"
            });

            setTimeout(() => {
                errorEquipo.elemento.focus();
            }, 300);

            return;
        }

        const equipos = [];

        document
            .querySelectorAll(".equipo-item")
            .forEach(item => {

                equipos.push({

                    serial:
                        item.querySelector(
                            "[data-serial]"
                        ).value,

                    marca:
                        item.querySelector(
                            "[data-marca]"
                        ).value,

                    tipo:
                        item.querySelector(
                            "[data-tipo]"
                        ).value,

                    modelo:
                        item.querySelector(
                            "[data-modelo]"
                        ).value,

                    inventario:
                        item.querySelector(
                            "[data-inventario]"
                        ).value,

                    gb:
                        item.querySelector(
                            "[data-gb]"
                        ).value

                });

            });

        if (
            !document.getElementById(
                "fecha"
            ).value
        ) {

            return;

        }

        const payload = {

            fecha:
                document.getElementById("fecha")?.value || "",

            entregado_a:
                document.getElementById("entregado_a")?.value || "",

            cargo_recibe:
                document.getElementById("cargo_recibe")?.value || "",

            entregado_por:
                document.getElementById("entregado_por")?.value || "",

            cargo_entrega:
                document.getElementById("cargo_entrega")?.value || "",

            asunto:
                document.getElementById("asunto")?.value || "",

            equipos:
                equipos

        };

        const response = await fetch(
            API_URL + "/generar-formateo-seguro",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            }
        );

        if (!response.ok) {

            const errorData =
                await response.json();

            throw new Error(
                errorData.mensaje ||
                "No fue posible generar la documentación"
            );

        }

        const result =
            await response.json();

        if (!result.success) {

            throw new Error(
                result.mensaje ||
                "Error generando la documentación"
            );

        }

        mostrarMensaje(
            "Documentación generada correctamente",
            "success"
        );

        const descargaResponse = await fetch(
            API_URL + "/descargar-acta/" +
            result.nombre_zip
        );

        if (!descargaResponse.ok) {
            throw new Error("Error descargando el archivo");
        }

        const blob =
            await descargaResponse.blob();

        const blobUrl =
            URL.createObjectURL(blob);

        const linkDescarga =
            document.createElement("a");

        linkDescarga.href = blobUrl;

        linkDescarga.download =
            result.nombre_zip;

        document.body.appendChild(
            linkDescarga
        );

        linkDescarga.click();

        linkDescarga.remove();

        URL.revokeObjectURL(blobUrl);


    }

    catch (error) {

        mostrarMensaje(
            "Error generando la documentación: " + error.message,
            "error"
        );

    }

}


/*
----------------------------------------------------
INICIALIZACIÓN
----------------------------------------------------
*/

/**
 * Inicializa la página al cargar el DOM.
 *
 * Acciones:
 * - Vincula botones de agregar equipo.
 * - Crea un equipo vacío por defecto.
 * - Limpia errores de validación al escribir en cualquier campo.
 * - Inicializa el datepicker en el campo de fecha.
 * - Habilita autocompletado de usuarios en entregado_a y entregado_por.
 */
document.addEventListener("DOMContentLoaded", () => {

    const btnEquipo =
        document.getElementById("btn-add-equipo");

    if (btnEquipo) {
        btnEquipo.addEventListener("click", agregarEquipo);
    }

    agregarEquipo();

    document
        .querySelectorAll(".input, .textarea")
        .forEach(campo => {

            campo.addEventListener("input", () => {

                if (campo.value.trim()) {

                    campo.classList.remove("is-invalid");

                    const helper =
                        campo.parentElement.querySelector(
                            ".helper-text"
                        );

                    if (helper) {
                        helper.style.display = "none";
                    }
                }

            });

        });

    flatpickr("#fecha", {
        dateFormat: "Y-m-d",
        monthSelectorType: "static",
        allowInput: true
    });

    const entregadoA =
        document.getElementById("entregado_a");

    const entregadoPor =
        document.getElementById("entregado_por");

    if (entregadoA) {
        initAutocomplete(entregadoA);
    }

    if (entregadoPor) {
        initAutocomplete(entregadoPor);
    }

});

/*
----------------------------------------------------
ADMINISTRACIÓN DINÁMICA DE EQUIPOS
----------------------------------------------------
*/

/**
 * Agrega un nuevo bloque de equipo al formulario.
 *
 * Cada bloque contiene: serial, botón buscar, marca, tipo,
 * modelo, inventario y cantidad en GB. Marca/tipo/modelo se
 * autocompletan desde GLPI al hacer click en "Buscar".
 * Se validan serial, inventario y GB antes de enviar.
 * Límite máximo: 4 equipos (capacidad de la plantilla DOCX).
 * Límite mínimo: 1 equipo (no se puede eliminar el último).
 */
function agregarEquipo() {

    const container =
        document.getElementById("equipos-container");

    if (
        container.querySelectorAll(".equipo-item").length >= MAX_EQUIPOS
    ) {

        mostrarMensaje(
            MSG_MAX_EQUIPOS,
            "warning"
        );

        return;
    }

    const numeroEquipo =
        container.querySelectorAll(".equipo-item").length + 1;

    const equipo =
        document.createElement("div");

    equipo.className = "equipo-item";

    equipo.innerHTML = `

        <div class="card border border-base-300 shadow-sm">

            <div class="card-body p-2">

                <div class="item-header">

                    <h4>
                        Equipo ${numeroEquipo}
                    </h4>

                    <button
                        type="button"
                        data-eliminar
                        class="btn btn-outline">

                        Eliminar

                    </button>

                </div>
                <div class="input-floating w-full mb-1">

                    <input
                        type="text"
                        class="input"
                        placeholder=" "
                        data-serial />

                    <label class="input-floating-label">

                        Serial

                    </label>

                </div>

                <button
                    type="button"
                    data-buscar
                    class="btn btn-outline mb-4">

                    Buscar

                </button>

                <div class="input-floating w-full mb-1">

                <input
                    class="input"
                    placeholder=" "
                    data-marca
                    disabled />

                <label class="input-floating-label">
                    Marca
                </label>

            </div>

            <div class="input-floating w-full mb-1">

                <input
                    class="input"
                    placeholder=" "
                    data-tipo
                    disabled />

                <label class="input-floating-label">
                    Tipo
                </label>

            </div>

            <div class="input-floating w-full mb-1">

                <input
                    class="input"
                    placeholder=" "
                    data-modelo
                    disabled />

                <label class="input-floating-label">
                    Modelo
                </label>

            </div>

            <div class="input-floating w-full mb-1">

                <input
                    class="input"
                    placeholder=" "
                    data-inventario />

                <label class="input-floating-label">
                    Inventario
                </label>

            </div>

            <div class="input-floating w-full">

                <input
                    class="input"
                    placeholder=" "
                    data-gb />

                <label class="input-floating-label">
                    Cantidad en GB
                </label>

            </div>

            </div>

        </div>

    `;

    container.appendChild(equipo);

    equipo
        .querySelectorAll(".input")
        .forEach(campo => {

            campo.addEventListener("input", () => {

                if (campo.value.trim()) {

                    campo.classList.remove("is-invalid");

                }

            });

        });

    renumerarEquipos();

    equipo
        .querySelector("[data-buscar]")
        .addEventListener("click", () => buscarEquipoBloque(equipo));

    equipo
        .querySelector("[data-eliminar]")
        .addEventListener("click", () => {

            if (
                document.querySelectorAll(".equipo-item").length === 1
            ) {

                mostrarMensaje(
                    "Debe existir al menos un equipo",
                    "warning"
                );

                return;

            }

            equipo.remove();

            renumerarEquipos();

        });

}

/*
----------------------------------------------------
UTILIDADES DE RENUMERACIÓN
----------------------------------------------------
*/

/*
----------------------------------------------------
VALIDACIONES
----------------------------------------------------
*/

/**
 * Valida los equipos agregados dinámicamente.
 *
 * En formateo seguro valida por equipo: serial, inventario y GB.
 * Delega el motor al helper compartido validarEquiposPorBloque.
 *
 * @returns {Object|null} Primer error: { elemento, nombre } o null si todo es válido.
 */
function validarEquipos() {

    return validarEquiposPorBloque((equipo, index) => [

        {
            elemento: equipo.querySelector("[data-serial]"),
            nombre: `Serial del Equipo ${index + 1}`
        },
        {
            elemento: equipo.querySelector("[data-inventario]"),
            nombre: `Inventario del Equipo ${index + 1}`
        },
        {
            elemento: equipo.querySelector("[data-gb]"),
            nombre: `Cantidad en GB del Equipo ${index + 1}`
        }

    ]);

}