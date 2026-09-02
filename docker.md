**dockerfile backend**

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/actas-glpi-1.0.0.jar app.jar

# Certificado intermedio que el servidor GLPI de produccion (sac-i.connser.com.co)
# NO envia en la cadena HTTPS. Sin el truststore de Java rechaza la conexion
# (SSLHandshakeException: PKIX path building failed) y la app devuelve datos vacios.
# Fuente: https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt
COPY certs/geotrust-tls-rsa-ca-g1.der /certs/geotrust-tls-rsa-ca-g1.der
RUN keytool -cacerts -storepass changeit -importcert -noprompt \
    -alias geotrust-tls-rsa-ca-g1 -file /certs/geotrust-tls-rsa-ca-g1.der

EXPOSE 8001

ENTRYPOINT ["java","-jar","app.jar"]

**dockerfile frontend**

FROM nginx:alpine

COPY . /usr/share/nginx/html

EXPOSE 80

**docker-compose.yml**

services:

  backend:
    build:
      context: ./backend

    container_name: actas-backend

    ports:
      - "8001:8001"

    env_file:
      - .env

  frontend:
    build:
      context: ./frontend

    container_name: actas-frontend

    ports:
      - "80:80"

    depends_on:
      - backend

---

## Certificado TLS de GLPI de produccion (sac-i.connser.com.co)

**Sintoma:** tras apuntar a produccion, la app muestra datos vacios (sin errores visibles).

**Causa raiz:** el servidor HTTPS de GLPI envia una cadena incompleta (solo el
certificado hoja `*.connser.com.co`, falta el intermedio `GeoTrust TLS RSA CA G1`).
El truststore de Java no puede construir la ruta de confianza y lanza
`SSLHandshakeException: PKIX path building failed`. Los servicios capturan la
excepcion en un catch generico y devuelven resultados vacios.

**Fix cliente:** importar el intermedio en el truststore de la JVM que corre el
backend (linea `RUN keytool ...` del Dockerfile). Justifica temporalmente el
despliegue mientras se corrige el origen.

**Fix servidor (correcto, pendiente del admin de GLPI):** completar la cadena en
la configuracion HTTPS de sac-i.connser.com.co anadiendo el certificado intermedio
`GeoTrust TLS RSA CA G1` (Apache `SSLCertificateChainFile` / nginx concatenado al
`ssl_certificate` / bundle del proxy inverso). Eso corrige la conexion para
cualquier cliente, no solo Java.

Intermedio descargado de: https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt
(guardado en backend/certs/geotrust-tls-rsa-ca-g1.der)