# syntax=docker/dockerfile:1

# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Dependencias primero: esta capa solo se invalida si cambia pom.xml
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Los tests se ejecutan en el pipeline de CI antes de construir la imagen
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests \
 && mv target/ms-picking-service-*.jar /workspace/app.jar


# ---------- Etapa 2: runtime (solo JRE, sin Maven ni código fuente) ----------
FROM eclipse-temurin:21-jre-alpine

# Usuario sin privilegios para no correr la app como root
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8084

# Healthcheck contra actuator; wget viene incluido en la imagen Alpine (busybox)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8084/actuator/health/liveness || exit 1

# MaxRAMPercentage: la JVM respeta el límite de memoria del contenedor.
# ExitOnOutOfMemoryError: falla rápido y Swarm reinicia la tarea en vez de quedar zombie.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
