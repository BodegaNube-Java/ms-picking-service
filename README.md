# ms-picking-service

Microservicio de picking y despacho de BodegaNube. **Versión actual: 1.0.0**

## Tecnologías

- Java 21 / Spring Boot 4.1.1
- Spring Data MongoDB (MongoDB 7)
- Docker (imagen multi-etapa) y Docker Swarm
- GitHub Actions para CI/CD

## Responsabilidad

- Recibir la notificación de que una orden quedó disponible para picking (uso interno, invocado por `ms-ordenes` una vez que el stock ya fue reservado).
- Listar al operario de bodega las órdenes disponibles para picking.
- Registrar el despacho de una orden y generar su número de seguimiento.

## Endpoints

```http
POST /picking/ordenes                 (uso interno, invocado por ms-ordenes)
GET  /picking/ordenes
POST /picking/ordenes/{ordenId}/despacho
```

Operativos (Actuator):

```http
GET /actuator/health/liveness   proceso vivo (lo usa el healthcheck de Docker)
GET /actuator/health            estado completo, incluye MongoDB
GET /actuator/info              versión desplegada (app.version)
```

Especificación completa en [`openapi.yaml`](openapi.yaml).

## Reglas de negocio

- No se puede registrar dos veces una orden de picking para el mismo `ordenId` (409 Conflict). La garantía real la da el índice único de MongoDB, incluso ante peticiones simultáneas.
- Solo se listan para picking las órdenes en estado `DISPONIBLE`.
- Una orden ya despachada no puede volver a despacharse (409 Conflict).
- Al registrar el despacho se genera un número de seguimiento único (`BN-XXXXXXXX`).

## Versionamiento

Se usa [Semantic Versioning](https://semver.org/lang/es/) (`MAJOR.MINOR.PATCH`):

| Cambio | Ejemplo | Versión |
|---|---|---|
| Cambio incompatible en la API o en el modelo de datos | quitar un campo de respuesta | MAJOR |
| Nueva funcionalidad compatible | nuevo endpoint | MINOR |
| Corrección de errores | fix de un 500 | PATCH |

Reglas del proyecto:

1. La versión vive en `pom.xml` (`<version>`) y es la única fuente de verdad. `openapi.yaml` debe coincidir.
2. Cada release se publica con un tag Git `vX.Y.Z` que **debe coincidir** con el `pom.xml`; si no coincide, el pipeline falla.
3. Las imágenes Docker se etiquetan así:
   - `ghcr.io/bodeganube-java/ms-picking-service:X.Y.Z` y `:latest` → releases (tag `vX.Y.Z`).
   - `ghcr.io/bodeganube-java/ms-picking-service:X.Y.Z-<sha7>` → cada build de `master`. Son inmutables y es la que se despliega.
4. Las versiones desplegadas se verifican en `/actuator/info`.

## Roles

```text
OPERARIO
```

> La validación del JWT (`bearerAuth` en `openapi.yaml`) aún no está implementada en este servicio: se espera que la verifique el API Gateway.

## Base de datos

MongoDB (`ms_picking`). Variable de entorno `MONGODB_URI` (por defecto `mongodb://localhost:27017/ms_picking`).

## Ejecución local

Ver [`EJECUCION_LOCAL.md`](EJECUCION_LOCAL.md) para la guía paso a paso.

```bash
./mvnw spring-boot:run
```

## Build y tests

```bash
./mvnw clean verify      # compila, ejecuta tests y empaqueta target/ms-picking-service-1.0.0.jar
```

Los tests necesitan MongoDB disponible en `MONGODB_URI` (por defecto `mongodb://localhost:27017`), porque el smoke test arranca la aplicación completa y crea el índice único:

```bash
docker run -d --name bodeganube-mongo -p 27017:27017 mongo:7
```

La lógica se prueba con Mockito y la capa web con `@WebMvcTest`. El job de CI levanta un servicio `mongo:7` automáticamente.

## Docker

```bash
docker build -t ms-picking-service:local .
docker run --rm -p 8084:8084 -e MONGODB_URI=mongodb://host.docker.internal:27017/ms_picking ms-picking-service:local
```

La imagen:

- Es multi-etapa: la imagen final sólo contiene el JRE de Alpine y el JAR.
- Corre con un usuario no root (`app`).
- Usa `MaxRAMPercentage=75` para respetar el límite de memoria del contenedor.
- Tiene `HEALTHCHECK` contra `/actuator/health/liveness`.

## Antes de desplegar sobre datos existentes

El servicio crea un **índice único en `ordenId`** al arrancar y se niega a iniciar si la colección ya tiene `ordenId` repetidos (la carrera de dos peticiones simultáneas pudo generar duplicados antes de esta versión). Antes del primer despliegue de la 1.0.0, revisar en Mongo:

```javascript
db.ordenes_picking.aggregate([
  { $group: { _id: "$ordenId", n: { $sum: 1 } } },
  { $match: { n: { $gt: 1 } } }
])
```

Si devuelve filas, resolver los duplicados antes de desplegar. Si Mongo no está disponible al arrancar, la réplica falla y Swarm la reintenta sin límite (el stack no define `max_attempts`) hasta que Mongo responda.

## Despliegue en Docker Swarm

### 1. Inicializar el clúster

En el primer nodo (será el **manager** inicial):

```bash
./scripts/swarm.sh init 10.0.1.10        # IP privada del nodo
```

### 2. Añadir nodos

```bash
# En el manager: obtener el comando de unión
./scripts/swarm.sh token worker          # o: ./scripts/swarm.sh token manager
```

Ejecutar el comando que imprime en cada nodo nuevo. Para tener alta disponibilidad del plano de control, usar **3 managers** (tolera la caída de 1; con 2 no hay quórum real). Verificar:

```bash
docker node ls
```

Puertos que deben estar abiertos entre nodos: `2377/tcp` (gestión), `7946/tcp+udp` (comunicación de nodos), `4789/udp` (overlay de red), y `8084/tcp` hacia el exterior.

### 3. Desplegar

```bash
./scripts/swarm.sh deploy ghcr.io/bodeganube-java/ms-picking-service:1.0.0
```

El script ejecuta `docker stack deploy` con `docker-compose.yml`. El stack define:

- `ms-picking` con 2 réplicas (ajustable con `PICKING_REPLICAS`).
- Actualizaciones sin caída: `start-first`, una réplica a la vez, con rollback automático si falla.
- `mongo` fijado a un manager, con volumen local `mongo-data`.

Verificar:

```bash
./scripts/swarm.sh status
curl http://<cualquier-nodo>:8084/actuator/info
```

### 4. Escalar

```bash
./scripts/swarm.sh scale 4     # sube a 4 réplicas
./scripts/swarm.sh scale 2     # baja a 2 réplicas
```

El routing mesh de Swarm balancea las peticiones entre todas las réplicas, sin importar en qué nodo estén.

### 5. Revertir

```bash
./scripts/swarm.sh rollback    # vuelve a la versión anterior del servicio
```

### Limitaciones conocidas

- Mongo corre como una única instancia con volumen local. Si el nodo manager que lo aloja cae, los datos no se pierden (el volumen queda en ese disco), pero el servicio no está disponible hasta que el nodo vuelva. Para producción real se recomienda un servicio gestionado (MongoDB Atlas o DocumentDB) o un replica set.

## CI/CD

Definido en [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml). Se ejecuta en PRs, en pushes a `master`, en tags `vX.Y.Z` y manualmente.

| Etapa | Qué hace |
|---|---|
| CI – build y tests | `./mvnw verify` (compila, testea, empaqueta) y sube el JAR como artefacto |
| CD – imagen | Calcula la versión, construye la imagen y la publica en GHCR |
| CD – Swarm | Copia `docker-compose.yml` al manager por SSH, despliega el stack, espera a que las réplicas estén arriba y valida `/actuator/health` y la versión en `/actuator/info` |

Las etapas de CD de imagen y despliegue sólo corren en `master` o en tags, no en PRs.

### Secretos y entorno

Configurar en **Settings → Secrets and variables → Actions**, con un entorno llamado `production`:

| Nombre | Descripción |
|---|---|
| `SWARM_HOST` | IP pública o DNS del manager |
| `SWARM_USER` | Usuario SSH en el manager (debe poder ejecutar `docker`) |
| `SWARM_SSH_KEY` | Llave privada SSH de ese usuario |
| `GHCR_USER` | Usuario de GitHub para que el clúster descargue la imagen |
| `GHCR_TOKEN` | Personal Access Token con permiso `read:packages` |

`GHCR_USER` y `GHCR_TOKEN` son obligatorios: el pipeline inicia sesión en GHCR desde el manager para descargar la imagen, aunque el paquete sea público.

### Publicar una versión

```bash
# 1. Subir el número en pom.xml (y openapi.yaml) y hacer merge a master
# 2. Crear el tag que coincide con esa versión
git tag v1.0.0
git push origin v1.0.0
```

## Estructura relevante

```text
Dockerfile                 imagen multi-etapa
docker-compose.yml         stack de Swarm
scripts/swarm.sh           init / token / deploy / scale / status / rollback
.github/workflows/ci-cd.yml pipeline CI/CD
openapi.yaml               contrato de la API
```
