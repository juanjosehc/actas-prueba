/*
====================================================
CONFIGURACIÓN DEL FRONTEND
====================================================

URL base del backend (API).
Centralizada en este archivo para facilitar el
despliegue (Docker, redes, dominios, etc.).

Resolución en orden de prioridad:
1. window.API_URL  → variable global inyectable en el
   despliegue (ej: un template nginx que la sustituye,
   o un script inline antes de este archivo).
2. location.hostname  → en producción interna (Docker
   o servidor real) se usa el host de la página con
   el puerto 8001 del backend. En desarrollo con Live
   Server (127.0.0.1:5501) resuelve a 127.0.0.1:8001.

Debe cargarse ANTES que el resto de scripts en los HTML.

====================================================
*/
const API_URL = (() => {

    if (typeof window !== "undefined" && window.API_URL) {
        return window.API_URL;
    }

    const host = window.location?.hostname || "127.0.0.1";

    return "http://" + host + ":8001";

})();
