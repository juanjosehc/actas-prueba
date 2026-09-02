/*
====================================================
CONFIGURACIÓN DEL FRONTEND
====================================================

URL base del backend (API).
Centralizada en este archivo.

Resolución en orden de prioridad:
1. window.API_URL  → variable global inyectable en el
   despliegue (ej: un script inline antes de este archivo,
   útil para override temporal sin tocar el repo).
2. URL de producción (Render)  → default fijo.
   En Vercel el hostname de la página NO tiene el backend,
   así que esta constante apunta directo a Render.

Debe cargarse ANTES que el resto de scripts en los HTML.

====================================================
*/
const API_URL = (() => {

    if (typeof window !== "undefined" && window.API_URL) {
        return window.API_URL;
    }

    // Backend Spring Boot desplegado en Render.
    return "https://actas-prueba.onrender.com";

})();