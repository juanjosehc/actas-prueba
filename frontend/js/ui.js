/*
====================================================
UI UTILITIES - FRONTEND
====================================================

Propósito:

Funciones compartidas de presentación utilizadas
por ambas páginas (acta de entrega y acta de
devolución).

Incluye el sistema centralizado de notificaciones
Toast:

- Posición superior derecha.
- Diseño moderno con icono según tipo.
- Animación suave de entrada y salida.
- Desaparición automática después de unos segundos.
- Tipos: success, warning, error, info.

Dependencias:

- Ninguna. Este archivo se carga primero.

====================================================
*/

/**
 * Tiempo (ms) que permanece visible cada notificación.
 */
const DURACION_TOAST = 3500;

/**
 * Iconos por tipo de notificación.
 */
const ICONOS_TOAST = {
    success: "✅",
    warning: "⚠️",
    error: "❌",
    info: "ℹ️"
};

/**
 * Obtiene o crea el contenedor de toasts.
 *
 * El contenedor se coloca fijo en la parte superior
 * derecha de la pantalla y agrupa todas las
 * notificaciones activas.
 *
 * @returns {HTMLElement} Contenedor #toast-container.
 */
function obtenerContenedorToast() {

    let contenedor =
        document.getElementById(
            "toast-container"
        );

    if (!contenedor) {

        contenedor =
            document.createElement("div");

        contenedor.id = "toast-container";

        document.body.appendChild(contenedor);

    }

    return contenedor;

}

/**
 * Muestra una notificación Toast.
 *
 * Crea un elemento dentro del contenedor #toast-container
 * con el texto y tipo indicados. El toast se anima al
 * entrar, permanece visible unos segundos y se elimina
 * automáticamente con animación de salida.
 *
 * Tipos soportados:
 * - "success": Operación exitosa (verde).
 * - "warning": Límite funcional alcanzado (ámbar).
 * - "error": Error de comunicación/backend (rojo).
 * - "info": Informativo por defecto (azul).
 *
 * @param {string} mensaje - Texto a mostrar.
 * @param {string} [tipo="info"] - Tipo de notificación.
 */
function mostrarMensaje(
    mensaje,
    tipo = "info"
) {

    const contenedor =
        obtenerContenedorToast();

    const toast =
        document.createElement("div");

    toast.className = `toast toast--${tipo}`;

    const icono =
        document.createElement("span");

    icono.className = "toast-icon";

    icono.textContent =
        ICONOS_TOAST[tipo] || ICONOS_TOAST.info;

    const texto =
        document.createElement("span");

    texto.className = "toast-message";

    texto.textContent = mensaje;

    toast.appendChild(icono);

    toast.appendChild(texto);

    contenedor.appendChild(toast);

    const eliminar = () => {

        toast.classList.add("is-out");

        setTimeout(() => {
            toast.remove();
        }, 300);

    };

    setTimeout(eliminar, DURACION_TOAST);

}

/*
----------------------------------------------------
VALIDACIONES Y UTILIDADES COMPARTIDAS DE EQUIPOS
----------------------------------------------------
*/

/**
 * Valida un campo obligatorio por su ID.
 *
 * Aplica la clase "is-invalid" y muestra el helper-text
 * si el campo está vacío. Remueve ambos si tiene valor.
 * Compartida por las páginas de entrega, devolución y
 * formateo seguro.
 *
 * @param {string} id - ID del elemento input a validar.
 * @returns {boolean} true si el campo tiene valor, false si está vacío.
 */
function validarCampo(id) {

    const campo =
        document.getElementById(id);

    const helper =
        campo.parentElement.querySelector(".helper-text");

    const vacio =
        !campo.value.trim();

    if (vacio) {

        campo.classList.add("is-invalid");

        if (helper) {
            helper.style.display = "block";
        }

        return false;
    }

    campo.classList.remove("is-invalid");

    if (helper) {
        helper.style.display = "none";
    }

    return true;
}

/**
 * Actualiza los títulos "Equipo N" después de agregar o eliminar.
 *
 * Recorre todos los .equipo-item y asigna el número
 * secuencial basado en su posición actual en el DOM.
 * Compartida por las páginas de entrega, devolución y
 * formateo seguro.
 */
function renumerarEquipos() {

    document
        .querySelectorAll(".equipo-item")
        .forEach((equipo, index) => {

            equipo.querySelector("h4").textContent =
                `Equipo ${index + 1}`;

        });

}

/**
 * Consulta GLPI por serial y auto completa marca, tipo y modelo.
 *
 * Endpoint: GET /equipo/{serial}
 * Los campos se actualizan dentro del bloque del equipo
 * al que pertenece el botón "Buscar".
 * Compartida por las páginas de entrega, devolución y
 * formateo seguro.
 *
 * @param {HTMLElement} bloque - Elemento .equipo-item que contiene los campos.
 */
async function buscarEquipoBloque(bloque) {

    const serial =
        bloque.querySelector("[data-serial]").value;

    try {

        const response =
            await fetch(`${API_URL}/equipo/${serial}`);

        if (!response.ok) {
            throw new Error("Respuesta no válida del servidor");
        }

        const data =
            await response.json();

        bloque.querySelector("[data-marca]").value =
            data.marca ?? "";

        bloque.querySelector("[data-tipo]").value =
            data.tipo ?? "";

        bloque.querySelector("[data-modelo]").value =
            data.modelo ?? "";

        if (
            data.marca ||
            data.tipo ||
            data.modelo
        ) {

            mostrarMensaje(
                "Equipo encontrado correctamente",
                "success"
            );

        }

    } catch (error) {

        mostrarMensaje(
            "Error al consultar información del equipo",
            "error"
        );

    }

}

/**
 * Valida los equipos agregados dinámicamente.
 *
 * Motor compartido: recibe una función que construye la
 * lista de campos obligatorios por cada .equipo-item según
 * la página (entrega: serial+inventario; devolución:
 * +estado; formateo: +gb). Retorna el primer error
 * encontrado para scroll automático y foco.
 *
 * @param {Function} obtenerCampos - (equipo, index) => [{ elemento, nombre }]
 * @returns {Object|null} Primer error: { elemento, nombre } o null si todo es válido.
 */
function validarEquiposPorBloque(obtenerCampos) {

    let primerError = null;

    document
        .querySelectorAll(".equipo-item")
        .forEach((equipo, index) => {

            const campos =
                obtenerCampos(equipo, index);

            campos.forEach(campo => {

                const vacio =
                    !campo.elemento?.value?.trim();

                if (vacio) {

                    campo.elemento.classList.add("is-invalid");

                    if (!primerError) {

                        primerError = {
                            elemento: campo.elemento,
                            nombre: campo.nombre
                        };

                    }

                } else {

                    campo.elemento.classList.remove("is-invalid");

                }

            });

        });

    return primerError;
}
