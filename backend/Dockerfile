# Imagen de solo ejecución (JRE). No necesita JDK ni Maven dentro del contenedor.
FROM eclipse-temurin:21-jre

# Directorio de trabajo. AppConfig busca ../.env relativo al CWD; en el
# contenedor apunta a / (no existe -> inofensivo, las env vars mandan).
WORKDIR /app

# El JAR se empaqueta en el host con `mvn clean package`.
COPY target/actas-glpi-1.0.0.jar app.jar

# Certificado intermedio que el servidor GLPI (sac-i.connser.com.co) no envía
# en la cadena HTTPS. Sin él, Java rechaza la conexión (PKIX path building
# failed). Fuente: https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt
COPY certs/geotrust-tls-rsa-ca-g1.der /certs/geotrust-tls-rsa-ca-g1.der
RUN keytool -cacerts -storepass changeit -importcert -noprompt \
    -alias geotrust-tls-rsa-ca-g1 -file /certs/geotrust-tls-rsa-ca-g1.der

EXPOSE 8001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]