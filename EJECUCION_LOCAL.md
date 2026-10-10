# Ejecución local — ms-picking-service

Guía paso a paso para clonar, instalar y ejecutar `ms-picking-service` en tu computador, y probar sus endpoints con Postman.

## Requisitos previos

- [Java 21 (JDK)](https://adoptium.net/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (para levantar MongoDB)
- [Postman](https://www.postman.com/downloads/) (o cualquier cliente HTTP similar)
- Git

## 1. Clonar el repositorio

```bash
git clone https://github.com/BodegaNube-Java/ms-picking-service.git
cd ms-picking-service
```

## 2. Levantar MongoDB con Docker

```bash
docker run -d --name bodeganube-mongo -p 27017:27017 mongo:7
```

Esto descarga (la primera vez) y levanta MongoDB en el puerto `27017`.

## 3. Configurar la variable de entorno de conexión

**Windows (PowerShell):**
```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/ms_picking"
```

**macOS / Linux:**
```bash
export MONGODB_URI="mongodb://localhost:27017/ms_picking"
```

> Esta variable debe estar definida en la **misma terminal** donde vas a ejecutar el microservicio.

## 4. Instalar dependencias y ejecutar

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Maven descarga automáticamente todas las dependencias del `pom.xml` en la primera ejecución. Cuando la consola muestre: 
Started MsPickingServiceApplication in X seconds


el microservicio ya está corriendo en `http://localhost:8084`.

## 5. Probar los endpoints con Postman

Importa o crea las siguientes 3 peticiones:

| Método | URL | Body (raw JSON) | Resultado esperado |
|---|---|---|---|
| `POST` | `http://localhost:8084/picking/ordenes` | `{"ordenId":"ORD-001","comercioId":"COM-001"}` | `201 Created`, orden en estado `DISPONIBLE` |
| `GET` | `http://localhost:8084/picking/ordenes` | — | `200 OK`, lista con `ORD-001` |
| `POST` | `http://localhost:8084/picking/ordenes/ORD-001/despacho` | — | `200 OK`, con `numeroSeguimiento` generado |

Para comprobar el manejo de errores, repite el primer `POST` con el mismo `ordenId` — debe responder `409 Conflict`.

## 6. Compilar y empaquetar (Maven)

```bash
# Windows
.\mvnw.cmd clean package

# macOS / Linux
./mvnw clean package
```

Esto genera el artefacto ejecutable en `target/ms-picking-service-0.0.1-SNAPSHOT.jar`. Para correrlo directamente desde el `.jar` (sin Maven):

```bash
java -jar target/ms-picking-service-0.0.1-SNAPSHOT.jar
```

(recuerda tener la variable `MONGODB_URI` definida antes de este comando también).

## 7. Detener el entorno

```bash
# Detener el microservicio: Ctrl+C en su terminal

# Detener y eliminar el contenedor de MongoDB
docker stop bodeganube-mongo
docker rm bodeganube-mongo
```
Tomcat started on port 8084
