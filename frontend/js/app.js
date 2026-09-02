/*
====================================================
ACTA DE ENTREGA - FRONTEND
====================================================

Responsabilidades:

- Gestión de datos del acta de entrega.
- Administración dinámica de equipos (agregar/eliminar/buscar).
- Administración dinámica de hardware (agregar/eliminar).
- Validaciones de formulario y equipos.
- Construcción del payload para el backend.
- Descarga automática del ZIP generado.

Endpoints utilizados:

- GET  /equipo/{serial}          → Consulta equipo en GLPI por serial.
- POST /generar-acta             → Genera acta + checklist (DOCX).
- GET  /descargar-acta/{archivo} → Descarga el ZIP generado.

Flujo principal:

1. Usuario completa campos obligatorios y opciones.
2. Click en "Generar Acta" ejecuta generarActa().
3. Se validan campos, sistema operativo y equipos.
4. Se construye el payload con todos los datos.
5. Se envía POST al backend.
6. Se recibe nombre del ZIP y se descarga automáticamente.

====================================================
*/

/*
----------------------------------------------------
LÍMITES DE REGISTROS (capacidad de plantillas DOCX)
----------------------------------------------------
*/
const MAX_EQUIPOS = 3;
const MAX_HARDWARE = 9;
const MSG_MAX_EQUIPOS = "Se alcanzó el máximo permitido de 3 equipos.";
const MSG_MAX_HARDWARE = "Se alcanzó el máximo permitido de 9 registros de Hardware y Software.";

/**
 * Genera el acta de entrega y la lista de chequeo.
 *
 * Flujo:
 * 1. Validar campos obligatorios (fecha, entregado_a, etc.).
 * 2. Validar que se haya seleccionado un sistema operativo.
 * 3. Validar que cada equipo tenga serial e inventario.
 * 4. Construir objetos de hardware, equipos y checklist.
 * 5. Armar el payload completo.
 * 6. Enviar POST a /generar-acta.
 * 7. Descargar el ZIP resultante vía /descargar-acta.
 */
async function generarActa() {

    try {

        const camposObligatorios = [

            "fecha",
            "entregado_a",
            "cargo_recibe",
            "entregado_por",
            "cargo_entrega",
            "asunto",
            "numero_sac"

        ];

        let primerCampoInvalido = null;

        camposObligatorios.forEach(id => {

            const valido = validarCampo(id);

            if (!valido && !primerCampoInvalido) {

                primerCampoInvalido =
                    document.getElementById(id);

            }

        });

        const sistemaOperativo =
            document.querySelector(
                'input[name="so"]:checked'
            );

        if (!sistemaOperativo) {

            document
                .querySelectorAll(
                    'input[name="so"]'
                )
                .forEach(radio => {

                    radio.classList.add(
                        "radio-so-error"
                    );

                });

            if (!primerCampoInvalido) {

                primerCampoInvalido =
                    document.getElementById(
                        "so-win10"
                    );

            }

        }
        else {

            document
                .querySelectorAll(
                    'input[name="so"]'
                )
                .forEach(radio => {

                    radio.classList.remove(
                        "radio-so-error"
                    );

                });

        }

        const errorEquipo =
            validarEquipos();

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

        const hardware = [];
        const checklist = {};

        for (let i = 1; i <= 36; i++) {

            checklist[`chk_${i}`] =
                document.getElementById(
                    `chk_${i}`
                )?.checked ?? false;

        }

        document
            .querySelectorAll(".hardware-item")
            .forEach(item => {

                hardware.push({

                    tipo:
                        item.querySelector(
                            "[data-tipo]"
                        ).value,

                    descripcion:
                        item.querySelector(
                            "[data-descripcion]"
                        ).value,

                    programa:
                        item.querySelector(
                            "[data-programa]"
                        ).value

                });

            });

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
                document.getElementById("fecha").value,

            entregado_a:
                document.getElementById("entregado_a").value,

            cargo_recibe:
                document.getElementById("cargo_recibe").value,

            entregado_por:
                document.getElementById("entregado_por").value,

            cargo_entrega:
                document.getElementById("cargo_entrega").value,

            asunto:
                document.getElementById("asunto").value,

            hardware:
                hardware,

            equipos:
                equipos,

            checklist:
                checklist,

            numero_sac:
                document.getElementById(
                    "numero_sac"
                ).value,
            
            observaciones:
                document.getElementById(
                    "observaciones"
                )?.value || "",

            sistema_operativo:
                document.querySelector(
                    'input[name="so"]:checked'
                )?.value || ""

        };        

        const response = await fetch(
            API_URL + "/generar-acta",
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
 * - Vincula botones de agregar hardware y equipo.
 * - Crea un equipo y un hardware vacíos por defecto.
 * - Vincula botones de marcar/desmarcar todo (checklist).
 * - Sincroniza "entregado_por" con "responsable_verificacion".
 * - Limpia errores de validación al escribir en cualquier campo.
 * - Limpia errores de selección de sistema operativo.
 * - Inicializa el datepicker en el campo de fecha.
 */
document.addEventListener("DOMContentLoaded", () => {

    const btnHardware =
        document.getElementById("btn-add-hardware");

    if (btnHardware) {
        btnHardware.addEventListener("click", agregarHardware);
    }

    const btnEquipo =
        document.getElementById("btn-add-equipo");

    if (btnEquipo) {
        btnEquipo.addEventListener("click", agregarEquipo);
    }

    agregarEquipo();
    agregarHardware();

    document
        .getElementById("btn-marcar-todo")
        ?.addEventListener("click", marcarTodosLosChecks);

    document
        .getElementById("btn-desmarcar-todo")
        ?.addEventListener("click", desmarcarTodosLosChecks);

    const entregadoPor =
        document.getElementById("entregado_por");

    const responsable =
        document.getElementById("responsable_verificacion");

    if (entregadoPor && responsable) {

        entregadoPor.addEventListener("input", () => {
            responsable.value = entregadoPor.value;
        });

    }

    const entregadoA =
        document.getElementById("entregado_a");

    if (entregadoA) {
        initAutocomplete(entregadoA);
    }

    if (entregadoPor) {
        initAutocomplete(entregadoPor);
    }

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

    document
        .querySelectorAll('input[name="so"]')
        .forEach(radio => {

            radio.addEventListener("change", () => {

                document
                    .getElementById("so-container")
                    ?.classList.remove("is-invalid");

                document
                    .querySelectorAll('input[name="so"]')
                    .forEach(item => {

                        item.classList.remove("radio-so-error");

                    });

            });

        });

    flatpickr("#fecha", {
        dateFormat: "Y-m-d",
        monthSelectorType: "static",
        allowInput: true
    });

});

/*
----------------------------------------------------
ADMINISTRACIÓN DINÁMICA DE HARDWARE
----------------------------------------------------
*/

/**
 * Agrega un nuevo registro de hardware al formulario.
 *
 * Cada registro contiene: tipo, descripción y programa.
 * Límite máximo: 9 registros (capacidad de la plantilla DOCX).
 * No se permite eliminar el último registro existente.
 */
function agregarHardware() {

    const container =
        document.getElementById("hardware-container");

    if (
        container.querySelectorAll(".hardware-item").length >= MAX_HARDWARE
    ) {

        mostrarMensaje(
            MSG_MAX_HARDWARE,
            "warning"
        );

        return;
    }

    const numeroHardware =
        container.querySelectorAll(".hardware-item").length + 1;

    const fila =
        document.createElement("div");

    fila.className = "hardware-item";

    fila.innerHTML = `

        <div class="card border border-base-300 shadow-md">

            <div class="card-body p-2">

                <div class="item-header">

                    <h4>
                        Hardware     ${numeroHardware}
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
                    data-tipo />

                <label class="input-floating-label">

                    Tipo Hardware

                </label>

            </div>

            <div class="input-floating w-full mb-1">

                <input
                    type="text"
                    class="input"
                    placeholder=" "
                    data-descripcion />

                <label class="input-floating-label">

                    Descripción

                </label>

            </div>

            <div class="input-floating w-full">

                <input
                    type="text"
                    class="input"
                    placeholder=" "
                    data-programa />

                <label class="input-floating-label">

                    Programa

                </label>

            </div>

            </div>

        </div>

    `;

    fila
        .querySelector("[data-eliminar]")
        .addEventListener("click", () => {

            if (
                document.querySelectorAll(".hardware-item").length === 1
            ) {

                mostrarMensaje(
                    "Debe existir al menos un hardware",
                    "warning"
                );

                return;

            }

            fila.remove();

            renumerarHardware();

        });

    container.appendChild(fila);

    renumerarHardware();

}

/*
----------------------------------------------------
ADMINISTRACIÓN DINÁMICA DE EQUIPOS
----------------------------------------------------
*/

/**
 * Agrega un nuevo bloque de equipo al formulario.
 *
 * Cada bloque contiene: serial, botón buscar, marca,
 * tipo, modelo e inventario. Marca/tipo/modelo se
 * autocompletan desde GLPI al hacer click en "Buscar".
 * Se validan serial, inventario y estado antes de enviar.
 * Límite máximo: 3 equipos (capacidad de la plantilla DOCX).
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

            <div class="input-floating w-full">

                <input
                    class="input"
                    placeholder=" "
                    data-inventario />

                <label class="input-floating-label">
                    Inventario
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

/**
 * Actualiza los títulos "Hardware N" después de agregar o eliminar.
 *
 * Mismo comportamiento que renumerarEquipos pero para
 * los bloques de hardware.
 */
function renumerarHardware() {

    document
        .querySelectorAll(".hardware-item")
        .forEach((hardware, index) => {

            hardware.querySelector("h4").textContent =
                `Hardware ${index + 1}`;

        });

}

/*
----------------------------------------------------
VALIDACIONES
----------------------------------------------------
*/

/**
 * Valida los equipos agregados dinámicamente.
 *
 * En entrega valida por equipo: serial e inventario.
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
        }

    ]);

}

/*
----------------------------------------------------
CHECKLIST - ACCORDIONS
----------------------------------------------------
*/

/**
 * Abre todos los acordeones del checklist.
 * Utilizado al marcar todos los checkboxes.
 */
function abrirTodosLosAccordions() {

    document
        .querySelectorAll(".check-section")
        .forEach(section => {

            section.open = true;

        });

}

/**
 * Cierra todos los acordeones del checklist.
 * Utilizado al desmarcar todos los checkboxes.
 */
function cerrarTodosLosAccordions() {

    document
        .querySelectorAll(".check-section")
        .forEach(section => {

            section.open = false;

        });

}

/**
 * Marca todos los checkboxes del checklist (chk_1 a chk_36).
 * También abre todos los acordeones para que el usuario
 * vea las opciones marcadas.
 */
function marcarTodosLosChecks() {

    document
        .querySelectorAll('input[type="checkbox"][id^="chk_"]')
        .forEach(check => {

            check.checked = true;

        });

    abrirTodosLosAccordions();

}

/**
 * Desmarca todos los checkboxes del checklist (chk_1 a chk_36).
 * También cierra todos los acordeones.
 */
function desmarcarTodosLosChecks() {

    document
        .querySelectorAll('input[type="checkbox"][id^="chk_"]')
        .forEach(check => {

            check.checked = false;

        });

    cerrarTodosLosAccordions();

}
