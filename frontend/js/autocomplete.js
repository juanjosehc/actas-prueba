/*
====================================================
AUTOCOMPLETADO DE USUARIOS GLPI - FRONTEND
====================================================

Componente reutilizable de autocompletado para los
campos de personas (Entregado a, Entregado por,
Recibido por).

Se conecta al endpoint del backend:

- GET /usuarios?texto=juan
  → Busca usuarios en GLPI por firstname, realname o login.
  → Retorna [{ id, nombreCompleto, login }].

Comportamiento:

- Busca a partir de "minChars" caracteres escritos.
- Debounce para no saturar el backend.
- Máximo "maxResultados" sugerencias visibles.
- Navegación con teclado (↑/↓/Enter/Escape).
- Selección con clic o Enter.
- Cierre al perder el foco o presionar Escape.
- Errores de comunicación → Toast tipo error.
- El campo sigue siendo editable manualmente
  (el autocompletado NO es obligatorio).

Uso:

    initAutocomplete(document.getElementById("entregado_a"), {
        minChars: 3,
        maxResultados: 10,
        debounceMs: 350
    });

El id del usuario seleccionado se guarda en el
atributo data-glpi-user-id del input. El valor del
input queda con el nombre completo.

Dependencias:

- ui.js (mostrarMensaje) para las notificaciones.

====================================================
*/

/**
 * URL del endpoint de búsqueda de usuarios.
 */
const USUARIOS_API_URL =
    API_URL + "/usuarios";

/**
 * Tiempo mínimo (ms) entre toasts de error repetidos
 * para no saturar la pantalla de notificaciones.
 */
const INTERVALO_ERROR_TOAST = 2000;

/**
 * Inicializa el autocompletado de usuarios GLPI
 * sobre un input de texto.
 *
 * @param {HTMLInputElement} input - Campo de texto objetivo.
 * @param {Object} [opciones] - Configuración opcional.
 * @param {number} [opciones.minChars=3] - Caracteres para buscar.
 * @param {number} [opciones.maxResultados=10] - Sugerencias máximas.
 * @param {number} [opciones.debounceMs=350] - Espera antes de buscar.
 */
function initAutocomplete(input, opciones = {}) {

    const config = {
        minChars: 3,
        maxResultados: 10,
        debounceMs: 350,
        ...opciones
    };

    let temporizador = null;
    let solicitudActual = null;
    let indiceActivo = -1;
    let resultados = [];
    let ultimoError = 0;
    let ignorarProximaEntrada = false;

    const contenedor =
        document.createElement("div");

    contenedor.className = "autocomplete";

    const contenedorPadre =
        input.closest(".input-floating") || input.parentElement;

    contenedorPadre.appendChild(contenedor);

    /**
     * Cierra el dropdown de sugerencias.
     */
    function cerrarLista() {

        contenedor.innerHTML = "";

        contenedor.classList.remove("is-open");

        indiceActivo = -1;

        resultados = [];

    }

    /**
     * Abre el dropdown y renderiza los resultados.
     *
     * @param {Array} items - Lista de usuarios [{ id, nombreCompleto }].
     */
    function mostrarLista(items) {

        contenedor.innerHTML = "";

        resultados = items;

        indiceActivo = -1;

        if (items.length === 0) {

            const vacio =
                document.createElement("div");

            vacio.className =
                "autocomplete-item autocomplete-item--sin-resultados";

            vacio.textContent =
                "Sin coincidencias";

            contenedor.appendChild(vacio);

        } else {

            items.forEach((usuario, indice) => {

                const item =
                    document.createElement("div");

                item.className =
                    "autocomplete-item";

                item.dataset.indice = String(indice);

                const nombre =
                    document.createElement("div");

                nombre.className =
                    "autocomplete-nombre";

                nombre.textContent =
                    usuario.nombreCompleto;

                item.appendChild(nombre);

                const login =
                    (usuario.login || "").trim();

                if (login) {

                    const lineaLogin =
                        document.createElement("div");

                    lineaLogin.className =
                        "autocomplete-login";

                    lineaLogin.textContent =
                        login;

                    item.appendChild(lineaLogin);

                }

                item.setAttribute(
                    "role",
                    "option"
                );

                item.setAttribute(
                    "aria-selected",
                    "false"
                );

                contenedor.appendChild(item);

            });

        }

        contenedor.classList.add("is-open");

    }

    /**
     * Aplica resaltado visual al ítem activo.
     */
    function resaltarActivo() {

        const items =
            contenedor.querySelectorAll(
                ".autocomplete-item"
            );

        items.forEach((item, indice) => {

            const activo =
                indice === indiceActivo;

            item.classList.toggle(
                "is-active",
                activo
            );

            item.setAttribute(
                "aria-selected",
                activo ? "true" : "false"
            );

            if (activo) {
                item.scrollIntoView({
                    block: "nearest"
                });
            }

        });

    }

    /**
     * Selecciona un usuario y lo escribe en el input.
     *
     * @param {Object} usuario - Usuario con id y nombreCompleto.
     */
    function seleccionar(usuario) {

        if (!usuario) {
            return;
        }

        input.value =
            usuario.nombreCompleto;

        input.dataset.glpiUserId =
            String(usuario.id);

        ignorarProximaEntrada = true;

        input.dispatchEvent(
            new Event("input", { bubbles: true })
        );

        cerrarLista();

    }

    /**
     * Consulta los usuarios al backend y renderiza.
     *
     * @param {string} texto - Texto de búsqueda.
     */
    async function buscar(texto) {

        if (solicitudActual) {
            solicitudActual.abort();
        }

        solicitudActual =
            new AbortController();

        try {

            const respuesta =
                await fetch(
                    `${USUARIOS_API_URL}?texto=${encodeURIComponent(texto)}`,
                    { signal: solicitudActual.signal }
                );

            if (!respuesta.ok) {
                throw new Error(
                    `HTTP ${respuesta.status}`
                );
            }

            const items =
                await respuesta.json();

            if (!Array.isArray(items)) {
                throw new Error(
                    "Formato de respuesta inválido"
                );
            }

            mostrarLista(
                items.slice(0, config.maxResultados)
            );

        } catch (error) {

            if (error.name === "AbortError") {
                return;
            }

            cerrarLista();

            const ahora =
                Date.now();

            if (
                ahora - ultimoError >=
                INTERVALO_ERROR_TOAST
            ) {

                ultimoError = ahora;

                mostrarMensaje(
                    "Error al consultar los usuarios",
                    "error"
                );

            }

        } finally {

            solicitudActual = null;

        }

    }

    /**
     * Programa la búsqueda con debounce según lo escrito.
     */
    function manejarEntrada() {

        clearTimeout(temporizador);

        if (ignorarProximaEntrada) {

            ignorarProximaEntrada = false;

            cerrarLista();

            return;

        }

        const texto =
            input.value.trim();

        if (texto.length < config.minChars) {
            cerrarLista();
            return;
        }

        temporizador =
            setTimeout(() => {
                buscar(texto);
            }, config.debounceMs);

    }

    input.addEventListener("input", manejarEntrada);

    input.addEventListener("focus", () => {
        manejarEntrada();
    });

    input.addEventListener("keydown", (evento) => {

        const abierto =
            contenedor.classList.contains("is-open");

        const items =
            contenedor.querySelectorAll(
                ".autocomplete-item"
            );

        switch (evento.key) {

            case "ArrowDown":

                if (abierto && items.length > 0) {

                    evento.preventDefault();

                    indiceActivo =
                        (indiceActivo + 1) % items.length;

                    resaltarActivo();

                }

                break;

            case "ArrowUp":

                if (abierto && items.length > 0) {

                    evento.preventDefault();

                    indiceActivo =
                        (indiceActivo - 1 + items.length) %
                        items.length;

                    resaltarActivo();

                }

                break;

            case "Enter":

                if (
                    abierto &&
                    indiceActivo >= 0 &&
                    resultados[indiceActivo]
                ) {

                    evento.preventDefault();

                    seleccionar(
                        resultados[indiceActivo]
                    );

                }

                break;

            case "Escape":

                if (abierto) {

                    evento.preventDefault();

                    cerrarLista();

                }

                break;

        }

    });

    input.addEventListener("blur", () => {

        setTimeout(() => {
            cerrarLista();
        }, 150);

    });

    contenedor.addEventListener("mousedown", (evento) => {

        evento.preventDefault();

        const item =
            evento.target.closest(
                ".autocomplete-item"
            );

        if (!item) {
            return;
        }

        const indice =
            Number(item.dataset.indice);

        if (
            indice >= 0 &&
            resultados[indice]
        ) {

            seleccionar(
                resultados[indice]
            );

        }

    });

    contenedor.addEventListener("mousemove", (evento) => {

        const item =
            evento.target.closest(
                ".autocomplete-item"
            );

        if (!item) {
            return;
        }

        const indice =
            Number(item.dataset.indice);

        if (indice >= 0 && indice !== indiceActivo) {

            indiceActivo = indice;

            resaltarActivo();

        }

    });

}
