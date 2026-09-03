/*
====================================================
CONFIGURACIÓN DEL FRONTEND
====================================================

URL base del backend (API). Debe cargarse ANTES que el
resto de scripts en los HTML.

Resolución:
1. window.API_URL → override global inyectable en el
   despliegue (script inline antes de este archivo),
   sin tocar el repo.
2. BACKEND_URL     → URL productiva fija abajo.

ADEMÁS: Ngrok Free muestra una pantalla intersticial en el
navegador salvo que la petición traiga el header
"ngrok-skip-browser-warning: true". Para no depender de que
cada fetch() lo agregue, se parchea window.fetch aquí (debajo)
y queda inyectado en TODAS las llamadas del frontend.
====================================================
*/
const BACKEND_URL = "https://waviness-makeover-suggest.ngrok-free.dev";

const API_URL = (() => {

    if (typeof window !== "undefined" && window.API_URL) {
        return window.API_URL;
    }

    return BACKEND_URL;

})();

// Ngrok Free: sin este header el navegador recibe la página de aviso
// intersticial en lugar de la respuesta JSON. Inyectado en todos los fetch.
(() => {
    const fetcher = window.fetch.bind(window);
    window.fetch = function (input, init) {
        init = init || {};
        init.headers = Object.assign(
            { "ngrok-skip-browser-warning": "true" },
            init.headers || {}
        );
        return fetcher(input, init);
    };
})();