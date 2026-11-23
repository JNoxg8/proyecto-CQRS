# Proyecto CQRS · Tienda de calzado con Kafka

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.8-231F20)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-escritura-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-lectura-47A248)
![Pruebas](https://img.shields.io/badge/pruebas-38%20JUnit-blue)

Proyecto académico — **Universidad Central**.

**Integrantes:** Juan Pablo Garcia · Kevin Estupinan Sotelo

La solución es una tienda de calzado en línea:

- **Pila de mensajería:** Apache Kafka, con dos tópicos de eventos.
- **CQRS** (*Command Query Responsibility Segregation*): la escritura (PostgreSQL) y la lectura (MongoDB) son
  servicios y bases de datos distintos, sincronizados por eventos.
- **Aplicación servidor:** 4 microservicios Spring Boot (autenticación, comandos, consultas y notificaciones).
- **Aplicaciones cliente:** Tienda (compradores), Admin (administrador) y Login, en HTML + JavaScript, con
  actualizaciones en vivo (SSE).
- **Seguridad:** inicio de sesión con JWT firmado (RS256) y roles: un cliente solo usa la tienda y el administrador
  tiene acceso total.
- **Tolerancia a fallos:** *Transactional Outbox*, reintentos con espera exponencial, *Dead Letter Topics*,
  reconstrucción del modelo de lectura y contenedores con reinicio automático y *healthchecks*.

📄 Documentación en Word (carpeta [`docs/`](docs/)):
- [Documentación del proyecto](docs/Documentacion_Proyecto_CQRS.docx): marco conceptual, arquitectura, modelo de
  datos, eventos, API, seguridad, pruebas, decisiones de diseño y limitaciones.
- [Guía de ejecución y demostración](docs/Guia_de_ejecucion_y_demostracion.docx): qué hacer paso a paso y dónde se
  ve reflejado cada paso (consolas, pgAdmin, Compass, Kafka UI y navegador).

## Contenido

- [Arquitectura](#arquitectura)
- [Flujo de una compra](#flujo-de-una-compra)
- [Roles y permisos](#roles-y-permisos)
- [Cómo ejecutarlo](#cómo-ejecutarlo)
- [API](#api)
- [Eventos](#eventos)
- [Tolerancia a fallos](#tolerancia-a-fallos)
- [Guion de demostración](#guion-de-demostración-6-minutos)
- [Decisiones de diseño](#decisiones-de-diseño)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Pruebas](#pruebas)

## Arquitectura

El diagrama sigue **una compra de arriba hacia abajo**. Los colores separan los dos lados de CQRS:
🟧 **escritura** (comandos + PostgreSQL) y 🟩 **lectura** (consultas + MongoDB), unidos solo por ⬛ **Kafka**.

```mermaid
flowchart TB
    subgraph CLI["🖥️ APLICACIONES CLIENTE"]
        LOGIN["🔑 <b>Login</b><br/>login.html"]
        TIENDA["🛒 <b>Tienda</b><br/>compradores"]
        ADMIN["🛠️ <b>Admin</b><br/>solo rol ADMIN"]
    end

    AUTH["🔐 <b>servicio-autenticacion</b> :8083<br/>revisa la contraseña y firma el JWT"]
    CMD["✍️ <b>servicio-comandos</b> :8081<br/>ESCRITURA: permisos y reglas de negocio"]
    PG[("<b>PostgreSQL</b> :5434<br/>fuente de verdad + tabla outbox")]
    KAFKA{{"📨 <b>PILA DE MENSAJERÍA</b><br/>Apache Kafka :9094<br/>calzado-eventos<br/>pedido-eventos"}}
    QRY["👀 <b>servicio-consultas</b> :8082<br/>LECTURA: responde GET y avisa en vivo (SSE)"]
    NOTIF["✉️ <b>servicio-notificaciones</b><br/>correos simulados y alertas de stock"]
    MONGO[("<b>MongoDB</b> :27018<br/>datos listos para mostrar")]

    LOGIN -->|"① inicia sesión → recibe un JWT"| AUTH
    TIENDA -->|"② comprar + JWT"| CMD
    ADMIN -->|"② crear, cambiar precio, despachar + JWT"| CMD
    CMD -->|"③ guarda pedido + evento en UNA transacción"| PG
    PG -->|"④ el relevo publica el evento"| KAFKA
    KAFKA -->|"⑤ entrega el evento"| QRY
    KAFKA -->|"⑤ entrega el mismo evento"| NOTIF
    QRY -->|"⑥ actualiza"| MONGO
    TIENDA -.->|"⑦ ver catálogo y pedidos (GET + SSE)"| QRY
    ADMIN -.->|"⑦ GET + SSE"| QRY

    classDef cliente fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a
    classDef escritura fill:#ffedd5,stroke:#c2410c,color:#7c2d12
    classDef lectura fill:#dcfce7,stroke:#15803d,color:#14532d
    classDef kafka fill:#262626,stroke:#000,color:#fff
    classDef otro fill:#f3e8ff,stroke:#7e22ce,color:#3b0764
    class LOGIN,TIENDA,ADMIN cliente
    class CMD,PG escritura
    class QRY,MONGO lectura
    class KAFKA kafka
    class AUTH,NOTIF otro
```

**Cómo leerlo:**

1. **Login** → `servicio-autenticacion` compara la contraseña (hash BCrypt) y entrega un **JWT** firmado. Los demás
   servicios verifican ese token con la clave pública que publica autenticación (`/.well-known/jwks.json`).
2. **Tienda** y **Admin** envían **comandos** (comprar, crear calzado, despachar…) con el JWT a `servicio-comandos`.
3. Comandos valida permisos y reglas (¿hay stock?) y guarda el cambio **y su evento** en PostgreSQL en una sola
   transacción (tabla `outbox_eventos`): o se guardan los dos, o ninguno.
4. Un **relevo** (*relay*) lee los eventos pendientes cada segundo y los publica en **Kafka**.
5. Kafka entrega **el mismo evento** a cada consumidor, cada uno con su grupo (*consumer group*): consultas y
   notificaciones lo procesan por su cuenta (publicar/suscribir, *pub/sub*).
6. `servicio-consultas` actualiza **MongoDB**, el modelo de lectura.
7. Los clientes **leen** solo de `servicio-consultas` (líneas punteadas) y reciben los cambios en vivo por SSE.

> **Lo más importante:** no hay ninguna flecha entre `servicio-comandos` y `servicio-consultas`. Escritura y lectura
> solo se comunican a través de Kafka; por eso, si la lectura se cae, se sigue vendiendo.

| Módulo | Qué hace | Tecnología |
|---|---|---|
| `eventos` | Contrato de mensajes compartido (records `CalzadoEvento`, `PedidoEvento`, nombres de tópicos) | Java puro |
| `servicio-autenticacion` | Registro, login, contraseñas con BCrypt y emisión de JWT firmados (RS256) | Spring Security, JPA, PostgreSQL |
| `servicio-comandos` | Recibe **comandos**, verifica permisos, aplica reglas de negocio, guarda y publica eventos | Spring Web, JPA, PostgreSQL, Kafka |
| `servicio-consultas` | **Proyecta** los eventos en MongoDB, responde consultas y empuja cambios en vivo (SSE) | Spring Web, MongoDB, Kafka |
| `servicio-notificaciones` | Segundo consumidor de los mismos eventos (otro *consumer group*) | Spring Kafka |
| `cliente-web` | Sirve las aplicaciones cliente: Tienda, Admin y Login | HTML, CSS, JavaScript |

### Las tres bases de datos

| Base | Motor | Dueño | Para qué |
|---|---|---|---|
| `tienda_comandos` | PostgreSQL | servicio-comandos | **Fuente de verdad**: tablas `calzados` y `pedidos` con transacciones, llave foránea y `@Version`, más `outbox_eventos` (eventos por enviar) |
| `tienda_consultas` | MongoDB | servicio-consultas | **Modelo de lectura**: colecciones `catalogo` y `pedidos`, desnormalizadas y listas para mostrar |
| `tienda_usuarios` | PostgreSQL | servicio-autenticacion | Tabla `usuarios` con contraseñas en hash BCrypt |

Cada servicio es dueño de sus datos: ningún servicio lee la base de otro. La única forma de comunicación entre
escritura y lectura son los eventos de Kafka.

## Flujo de una compra

```mermaid
sequenceDiagram
    actor C as Cliente (tienda.html)
    participant A as servicio-autenticacion
    participant W as servicio-comandos
    participant PG as PostgreSQL
    participant K as Kafka
    participant R as servicio-consultas
    participant M as MongoDB
    participant N as servicio-notificaciones
    actor AD as Admin (admin.html)

    C->>A: POST /api/auth/login
    A-->>C: JWT firmado (RS256)
    C->>W: POST /api/pedidos {calzadoId, cantidad} + Bearer JWT
    W->>W: verifica firma y rol, valida stock
    W->>PG: INSERT pedido + UPDATE stock + INSERT outbox_eventos (una sola transacción)
    W-->>C: 201 {id, mensaje}
    W->>K: relevo del outbox (cada 1 s): PEDIDO_CREADO y STOCK_DESCONTADO
    W->>PG: marca los eventos como enviados (Kafka confirmó)
    K->>R: consume (grupos consultas-*)
    R->>M: upsert del pedido y del calzado
    R-->>C: SSE: su pedido
    R-->>AD: SSE: todos los pedidos
    K->>N: consume (grupo notificaciones)
    N->>N: [CORREO SIMULADO] y alerta de stock bajo
```

El comando responde **antes** de que MongoDB se actualice: la lectura se pone al día milisegundos después. Eso es
la **consistencia eventual** (*eventual consistency*) y la tienda muestra cuánto tardó.

## Roles y permisos

| Acción | Visitante | CLIENTE | ADMIN |
|---|:-:|:-:|:-:|
| Ver catálogo (y sus cambios en vivo) | ✅ | ✅ | ✅ |
| Comprar | ❌ 401 | ✅ | ✅ |
| Ver los pedidos propios / recibirlos en vivo | ❌ 401 | ✅ solo los suyos | ✅ |
| Cancelar un pedido | ❌ 401 | ✅ solo los suyos (si no, 403) | ✅ cualquiera |
| Ver todos los pedidos | ❌ 401 | ❌ 403 | ✅ |
| Registrar calzado, cambiar precio, reponer stock | ❌ 401 | ❌ 403 | ✅ |
| Despachar pedidos | ❌ 401 | ❌ 403 | ✅ |
| Reconstruir el modelo de lectura | ❌ 401 | ❌ 403 | ✅ |
| Abrir `admin.html` | → login | → login | ✅ |

- **401 Unauthorized** = el servidor no identifica al usuario (token ausente, vencido o alterado).
- **403 Forbidden** = el usuario está identificado, pero no tiene permiso.
- La protección real está en las **APIs** (`SeguridadConfig` de cada servicio). Que el navegador oculte el panel de
  admin es solo comodidad: cualquiera podría llamar a la API con curl o Talend.
- Registrarse siempre crea un **CLIENTE**. Los administradores solo se crean desde la configuración del servidor.

**Cuentas de demostración** (solo para desarrollo): están en
`servicio-autenticacion/src/main/resources/application.yml` → `tienda.usuarios-iniciales` (un ADMIN y un CLIENTE).

### Cómo funciona el token
1. `POST /api/auth/login` → el servicio de autenticación verifica la contraseña (BCrypt) y devuelve un **JWT**
   firmado con su clave **privada** RSA. Contenido: `sub` (correo), `nombre`, `roles`, `exp` (2 horas).
2. El navegador lo guarda en `sessionStorage` (por pestaña) y lo envía en cada petición:
   `Authorization: Bearer <token>`. Para el canal en vivo (EventSource no permite cabeceras) va en
   `?access_token=` y **solo** en `/api/stream`.
3. Comandos y consultas descargan la clave **pública** (`/.well-known/jwks.json`) y verifican firma y vencimiento
   **sin consultar** al servicio de autenticación. Si alguien modifica el token (por ejemplo, se pone `ADMIN`),
   la firma ya no coincide → 401.
4. La identidad sale del token, **nunca** del cuerpo o de un parámetro: el pedido no lleva correo, y "mis pedidos"
   es `/api/pedidos/mios` (no `?email=`), para que nadie lea pedidos ajenos cambiando un correo.

## Cómo ejecutarlo

Requisitos: Git, Docker Desktop encendido y **JDK 21** (Maven no hace falta: el proyecto trae `mvnw`).

1. Se clona el proyecto y se levanta la infraestructura (PostgreSQL, MongoDB, Kafka y Kafka UI):
   ```powershell
   git clone https://github.com/JNoxg8/proyecto-CQRS.git
   cd proyecto-CQRS
   docker compose up -d
   ```
   La primera vez, `docker/postgres-init/01-bases.sql` crea la base `tienda_usuarios`. Si el volumen de Postgres
   ya existía antes de agregar la autenticación, la base se crea una sola vez de forma manual:
   ```powershell
   docker exec tienda-postgres psql -U tienda -d tienda_comandos -c "CREATE DATABASE tienda_usuarios OWNER tienda;"
   ```
2. En IntelliJ: **File → Open** esta carpeta (proyecto Maven), SDK = JDK 21, y se ejecuta
   **`Tienda: TODO (servidor + clientes)`** (o cada servicio del 1 al 5).

   Sin IntelliJ:
   ```powershell
   .\mvnw.cmd -DskipTests package
   java -jar servicio-autenticacion\target\servicio-autenticacion-1.0.0.jar   # una terminal por servicio
   java -jar servicio-comandos\target\servicio-comandos-1.0.0.jar
   java -jar servicio-consultas\target\servicio-consultas-1.0.0.jar
   java -jar servicio-notificaciones\target\servicio-notificaciones-1.0.0.jar
   java -jar cliente-web\target\cliente-web-1.0.0.jar
   ```
3. Se abre <http://localhost:8080> (la tienda). El panel de admin está en <http://localhost:8080/admin.html> y pide
   iniciar sesión como ADMIN. Kafka UI: <http://localhost:8090>.

| Recurso | Dirección | Credenciales |
|---|---|---|
| Clientes web | http://localhost:8080 | cuentas de demostración (ver arriba) |
| API de autenticación | http://localhost:8083/api/auth | — |
| API de comandos | http://localhost:8081/api | JWT |
| API de consultas | http://localhost:8082/api | JWT (catálogo público) |
| Kafka UI | http://localhost:8090 | — (herramienta interna, no exponer) |
| PostgreSQL (pgAdmin) | localhost:**5434**, BDs `tienda_comandos` y `tienda_usuarios` | `tienda` / `tienda123` |
| MongoDB (Compass) | `mongodb://admin:admin@localhost:27018/?authSource=admin`, BD `tienda_consultas` | `admin` / `admin` |

## API

**Autenticación** (`servicio-autenticacion`):

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/auth/registro` | Crea una cuenta CLIENTE y devuelve su token (`{nombre, email, password}`) |
| POST | `/api/auth/login` | Devuelve `{token, expiraEn, nombre, email, rol}` (401 si las credenciales no coinciden) |
| GET | `/.well-known/jwks.json` | Clave pública para verificar tokens |

**Comandos** (`servicio-comandos`, solo escriben; devuelven `{id, mensaje}`):

| Método | Ruta | Comando | Rol |
|---|---|---|---|
| POST | `/api/pedidos` | Crear pedido `{calzadoId, cantidad}` (409 si no hay stock) | CLIENTE, ADMIN |
| POST | `/api/pedidos/{id}/cancelar` | Cancelar y devolver stock (solo CREADO) | dueño o ADMIN |
| POST | `/api/pedidos/{id}/despachar` | Despachar (solo CREADO) | ADMIN |
| POST | `/api/calzados` | Registrar calzado | ADMIN |
| PUT | `/api/calzados/{id}/precio` | Cambiar precio | ADMIN |
| POST | `/api/calzados/{id}/reponer-stock` | Reponer stock | ADMIN |

**Consultas** (`servicio-consultas`, solo leen):

| Método | Ruta | Qué devuelve | Rol |
|---|---|---|---|
| GET | `/api/catalogo?marca=&categoria=&talla=&precioMax=&soloDisponibles=` | Catálogo filtrado | público |
| GET | `/api/catalogo/{id}` | Un calzado | público |
| GET | `/api/stream` | Canal en vivo (SSE), filtrado según el usuario | público / con token |
| GET | `/api/pedidos/mios` | Pedidos del usuario autenticado | autenticado |
| GET | `/api/pedidos`, `/api/pedidos/{id}` | Todos los pedidos / uno | ADMIN |
| POST | `/api/admin/reconstruir` | Borra MongoDB y reproduce todos los eventos de Kafka | ADMIN |

Errores con formato *Problem Details* (RFC 7807): `400` datos inválidos, `401` no autenticado, `403` sin permiso,
`404` no existe, `409` regla de negocio o conflicto de concurrencia.

Pruebas listas en `requests.http` (IntelliJ guarda los tokens automáticamente) y `tienda_talend_collection.json`
(en Talend se ejecuta el login y el `token` obtenido se copia en la cabecera `Authorization` de las demás peticiones).

## Eventos

| Tópico (clave) | Eventos |
|---|---|
| `calzado-eventos` (calzadoId) | `CALZADO_REGISTRADO`, `PRECIO_CAMBIADO`, `STOCK_REPUESTO`, `STOCK_DESCONTADO`, `STOCK_DEVUELTO` |
| `pedido-eventos` (pedidoId) | `PEDIDO_CREADO`, `PEDIDO_DESPACHADO`, `PEDIDO_CANCELADO` |
| `calzado-eventos.DLT`, `pedido-eventos.DLT` | *Dead Letter Topics*: mensajes que un consumidor no pudo procesar (ver [Tolerancia a fallos](#tolerancia-a-fallos)) |

Los tópicos de eventos tienen **retención infinita** (`retention.ms=-1`): por defecto Kafka borra los mensajes de más
de 7 días, y aquí los tópicos son el historial con el que se reconstruye el modelo de lectura.

Cada evento lleva el **estado completo** de la entidad (*event-carried state transfer*), por eso la proyección
es un simple *upsert* idempotente. Por el canal en vivo, los eventos de calzado llegan a todos, pero un evento de
pedido (con nombre y correo) solo llega al ADMIN y al dueño del pedido.

## Tolerancia a fallos

| Falla | Qué hace el sistema | Mecanismo |
|---|---|---|
| **Kafka caído** | Las compras **siguen funcionando** (201). Los eventos esperan en la tabla `outbox_eventos` y se envían en orden cuando Kafka vuelve. | *Transactional Outbox* (`RegistradorOutbox` + `RelevoOutbox`) |
| **MongoDB caído** | Se puede seguir comprando. `servicio-consultas` reintenta cada evento con espera exponencial (1 s, 2 s, 4 s, 8 s, 10 s…) durante ~2 min; si Mongo vuelve, no se pierde nada. | `DefaultErrorHandler` + `ExponentialBackOff` (`ErroresKafkaConfig`) |
| **Mensaje corrupto o que falla siempre** | No bloquea al consumidor ni se descarta en silencio: se copia a `<tópico>.DLT` con cabeceras que dicen la excepción, el *offset* y el grupo que falló. | *Dead Letter Topic* (`DeadLetterPublishingRecoverer`) |
| **`servicio-consultas` o `servicio-notificaciones` apagado** | La escritura no se entera. Al volver, cada grupo retoma desde su último *offset* confirmado. | Grupos de consumidores de Kafka |
| **Se pierde MongoDB entero** | `POST /api/admin/reconstruir` vacía el modelo de lectura y reproduce todos los eventos desde el *offset* 0. | Retención infinita + proyecciones idempotentes |
| **El servicio de comandos muere a mitad de envío** | Al reiniciar, el relevo reenvía lo que no quedó marcado. Puede llegar un duplicado, pero la proyección es un *upsert* idempotente. | Entrega *al menos una vez* (*at-least-once*) |
| **Dos compras simultáneas del último par** | Una gana y la otra recibe 409. | Bloqueo optimista (`@Version`) |
| **Un contenedor se cae** | Docker lo vuelve a iniciar; `docker ps` muestra si está `healthy`. Los datos (incluido el historial de Kafka) viven en volúmenes. | `restart: unless-stopped`, `healthcheck`, volúmenes con nombre |

**Por qué el Outbox.** Antes, el evento se enviaba a Kafka *después* del commit. Si Kafka estaba caído en ese
momento, la compra quedaba guardada pero el evento se perdía, y la escritura y la lectura quedaban inconsistentes para
siempre (problema de la **doble escritura**, *dual write*). Ahora el evento se guarda en PostgreSQL **en la misma
transacción** que la compra: o se guardan los dos o ninguno. Un relevo programado lo envía después, espera la
confirmación de Kafka (`acks=all`, productor idempotente) y solo entonces lo marca como enviado.

**Demostración de fallos** (con todos los servicios arriba):

```powershell
# 1) Kafka caído: comprar sigue funcionando
docker stop tienda-kafka
#    → se compra en la tienda: "Pedido #N aceptado por el servicio de COMANDOS... Esperando que llegue al servicio de CONSULTAS"
docker exec tienda-postgres psql -U tienda -d tienda_comandos -c "select id, tipo, enviado_en from outbox_eventos order by id desc limit 4;"
#    → los eventos nuevos tienen enviado_en vacío (pendientes)
docker start tienda-kafka
#    → a los pocos segundos: "✓ Pedido #N ya está en MongoDB ... ms después" (SSE) y la consola de comandos dice "Kafka disponible de nuevo"

# 2) MongoDB caído: reintentos con espera
docker stop tienda-mongo
#    → se compra; la consola de consultas muestra "Fallo procesando ... (intento N). Se reintentara con espera."
docker start tienda-mongo
#    → el pedido se proyecta sin perder nada

# 3) Mensaje corrupto → Dead Letter Topic
docker exec tienda-kafka sh -c 'echo prueba:esto-no-es-json | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9094 --topic calzado-eventos --property parse.key=true --property key.separator=:'
#    → en Kafka UI (http://localhost:8090) aparece en calzado-eventos.DLT, una vez por cada grupo consumidor
```

## Guion de demostración (6 minutos)

1. Se abre la **Tienda** sin iniciar sesión: el catálogo se ve, pero comprar pide iniciar sesión.
2. Al intentar abrir `admin.html`, el sistema redirige al login con el aviso "solo para administradores".
3. Se inicia sesión como **cliente** en una ventana y como **admin** en otra (o en otra pestaña), con la
   consola de `servicio-notificaciones` a la vista.
4. Se realiza una compra como cliente. Se observa:
   - el aviso "*Pedido #N ya está en MongoDB X ms después*" → **consistencia eventual**;
   - el Admin se actualiza solo (SSE) y aparece el `[CORREO SIMULADO]` → **pub/sub**.
5. Se crea una segunda cuenta de cliente y se realiza una compra: el primer cliente **no** ve ese pedido (ni en la tabla ni en vivo).
6. Desde Talend, con el token del cliente, se intenta registrar un calzado → **403**; sin token → **401**.
7. En el Admin se **cambia un precio**: los pedidos viejos conservan su precio (**modelo desnormalizado**).
8. Se pulsa **Reconstruir modelo de lectura**: MongoDB se vacía y se reconstruye desde Kafka (**event replay**).
9. En pgAdmin, en `tienda_usuarios.usuarios`, se verifica que las contraseñas están como hash BCrypt (`$2a$10$...`).
10. **Tolerancia a fallos**: `docker stop tienda-kafka`, se compra (funciona), se muestra el evento pendiente en
    `outbox_eventos`, `docker start tienda-kafka` y el pedido aparece solo (ver [Tolerancia a fallos](#tolerancia-a-fallos)).

## Decisiones de diseño

- **Comandos con intención** (`CrearPedido`, `ReponerStock`) en vez de CRUD genérico; los comandos no devuelven datos.
- **Reglas de negocio dentro de las entidades** (`Calzado.descontarStock`, `Pedido.cancelar`), probadas con JUnit.
- **`@Version` (bloqueo optimista)**: dos compras simultáneas del último par no pueden vender de más.
- **Transactional Outbox**: el evento se guarda en la misma transacción que el cambio y un relevo lo envía a Kafka.
  Kafka nunca anuncia algo que se revirtió, y un evento confirmado nunca se pierde aunque Kafka esté caído.
- **Reintentos con espera exponencial + Dead Letter Topic** en los consumidores: una falla temporal se supera sola y
  una permanente queda guardada para revisarla, en vez de descartarse.
- **Clave del mensaje = id** de la entidad: los eventos de un mismo calzado llegan en orden aunque haya 3 particiones.
- **`ErrorHandlingDeserializer`**: un mensaje corrupto no bloquea al consumidor para siempre.
- **Seguridad**: JWT RS256 (solo autenticación puede firmar), BCrypt, mismo mensaje para correo inexistente y clave
  incorrecta (evita enumerar usuarios), lista blanca de rutas (`denyAll` por defecto), CORS limitado a
  `localhost:8080`, escape de HTML contra XSS, validación con Bean Validation, base de datos separada para usuarios.

### Límites conocidos (mejoras posibles)

- Kafka es un solo *broker* con factor de replicación 1: si su disco se daña, se pierde el historial de eventos.
  En producción serían 3 *brokers* con `replication.factor=3` y `min.insync.replicas=2`.
- El relevo del outbox está pensado para **una** instancia de `servicio-comandos`; con varias haría falta un bloqueo
  (`SELECT ... FOR UPDATE SKIP LOCKED`) o una herramienta de CDC como Debezium.
- La entrega es *al menos una vez*: tras una caída, `servicio-notificaciones` podría repetir un correo simulado
  → guardar los ids de eventos ya procesados (consumidor idempotente).
- Un JWT no se puede revocar al instante: sigue valiendo hasta que vence (2 h) → expiración corta + *refresh tokens*.
- El token en `sessionStorage` es legible por JavaScript: un XSS podría robarlo → cookies `HttpOnly` con un BFF.
- No hay límite de intentos de login → *rate limiting* / bloqueo temporal.
- La clave RSA se genera al arrancar; en producción vendría de un almacén de secretos (*vault*).
- Kafka UI no tiene autenticación: es una herramienta interna que no debe exponerse.

## Estructura del repositorio

```
proyecto-CQRS/
├── docker-compose.yml           # PostgreSQL :5434, MongoDB :27018, Kafka :9094, Kafka UI :8090 (con healthchecks)
├── docker/postgres-init/        # crea la base tienda_usuarios la primera vez
├── pom.xml                      # proyecto Maven padre (multi-módulo)
├── eventos/                     # contrato compartido: records de eventos, enums y nombres de tópicos
├── servicio-autenticacion/      # :8083 login, registro, BCrypt, JWT RS256, JWKS
│   └── src/main/java/com/tienda/autenticacion/{api, aplicacion, dominio, token, config}
├── servicio-comandos/           # :8081 lado de ESCRITURA
│   └── src/main/java/com/tienda/comandos/{api, comandos, aplicacion, dominio, repositorio, mensajeria, config}
├── servicio-consultas/          # :8082 lado de LECTURA
│   └── src/main/java/com/tienda/consultas/{api, aplicacion, modelo, proyeccion, repositorio, tiemporeal, config}
├── servicio-notificaciones/     # consumidor independiente (correos simulados, alertas de stock)
├── cliente-web/                 # :8080 sirve las páginas
│   └── src/main/resources/static/{tienda.html, admin.html, login.html, js/, css/}
├── .run/                        # configuraciones de ejecución de IntelliJ ("Tienda: TODO")
├── requests.http                # pruebas de la API para IntelliJ (guarda los tokens solo)
├── tienda_talend_collection.json# las mismas pruebas para Talend API Tester
└── docs/                        # documentación en Word
```

Cada servicio sigue capas simples: `api` (controladores REST) → `aplicacion` (casos de uso) → `dominio`
(entidades y reglas de negocio) → `repositorio` (acceso a datos), más `mensajeria`/`proyeccion` para Kafka y
`config` para seguridad, CORS y datos iniciales.

## Pruebas

```powershell
.\mvnw.cmd test
```
38 pruebas: reglas de negocio, proyecciones, login/registro/firma de tokens, la **matriz de permisos** de cada API
(con tokens simulados) y el **outbox** (qué se guarda, el orden de envío y que un evento no se salta si Kafka está
caído). No necesitan Docker.
