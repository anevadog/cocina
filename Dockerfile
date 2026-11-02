# syntax=docker/dockerfile:1
# Imagen de la API: build multi-stage, JRE 25 y usuario sin privilegios.
# El build usa el Maven Wrapper, así que la imagen no depende de un Maven instalado.

# --- Etapa de build -------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# Las dependencias se resuelven antes de copiar el código para aprovechar la caché de capas.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

# --- Etapa de ejecución ---------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build /build/target/cocina-*.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
