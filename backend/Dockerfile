# =====================================================
# Build multi-stage: compila el JAR dentro del contenedor.
# Render no tiene target/ del repo (no se sube a git), por
# eso Maven corre aquí, no en el host.
# =====================================================

# --- Etapa 1: build (JDK 21 + Maven) ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencias primero (capa de caché: no se re-baja si pom.xml no cambia).
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Etapa 2: runtime (JRE 21) ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/actas-glpi-1.0.0.jar app.jar

# Certificado intermedio que el servidor GLPI (sac-i.connser.com.co) no envía
# en la cadena HTTPS. Sin él, Java rechaza la conexión (PKIX path building failed).
COPY certs/geotrust-tls-rsa-ca-g1.der /certs/geotrust-tls-rsa-ca-g1.der
RUN keytool -cacerts -storepass changeit -importcert -noprompt \
    -alias geotrust-tls-rsa-ca-g1 -file /certs/geotrust-tls-rsa-ca-g1.der

# El puerto real lo asigna Render vía $PORT (ver application.yml: ${PORT:8001}).
EXPOSE 8001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]