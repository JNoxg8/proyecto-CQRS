# Proyecto CQRS · Tienda de calzado con Kafka

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.8-231F20)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-escritura-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-lectura-47A248)
![Pruebas](https://img.shields.io/badge/pruebas-35%20JUnit-blue)

Proyecto académico de ingeniería de software. Enunciado: *"desarrollar una solución que tenga una pila de
mensajería, que tenga CQRS, aplicación servidor y aplicaciones cliente"*.

La solución es una tienda de calzado en línea:

- **Pila de mensajería:** Apache Kafka, con dos tópicos de eventos.
- **CQRS** (*Command Query Responsibility Segregation*): la escritura (PostgreSQL) y la lectura (MongoDB) son
  servicios y bases de datos distintos, sincronizados por eventos.
- **Aplicación servidor:** 4 microservicios Spring Boot (autenticación, comandos, consultas y notificaciones).
- **Aplicaciones cliente:** Tienda (compradores), Admin (administrador) y Login, en HTML + JavaScript, con
  actualizaciones en vivo (SSE).
- **Seguridad:** inicio de sesión con JWT firmado (RS256) y roles: un cliente solo usa la tienda y el administrador
  tiene acceso total.

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
- [Guion de demostración](#guion-de-demostración-6-minutos)
- [Decisiones de diseño](#decisiones-de-diseño)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Pruebas](#pruebas)

## Arquitectura

```
 ┌──────────────────┐   ┌──────────────────┐   ┌────────────┐
 │ Cliente TIENDA   │   │ Cliente ADMIN    │   │ Login /    │      APLICACIONES CLIENTE (cliente-web :8080)
 │ visitante o      │   │ solo rol ADMIN   │   │ registro   │
 │ rol CLIENTE      │   │                  │   │            │
 └──┬───────────▲───┘   └──┬───────────▲───┘   └─────┬──────┘
    │ comandos  │ GET+SSE  │ comandos  │ GET+SSE      │ login → JWT
    │ + JWT     │ + JWT    │ + JWT     │ + JWT        ▼
    │           │          │           │      ┌──────────────────────────┐
    │           │          │           │      │ servicio-autenticacion   │
    │           │          │           │      │ :8083 usuarios (BCrypt)  │
    │           │          │           │      │ firma JWT RS256          │
    │           │          │           │      │ PostgreSQL tienda_usuarios│
    │           │          │           │      └────────────┬─────────────┘
    ▼           │          ▼           │                   │ clave PÚBLICA (/.well-known/jwks.json)
 ┌─────────────────────┐        ┌──────┴──────────────┐    │
 │ servicio-comandos   │◄───────┼─────────────────────┼────┘ ambos verifican la firma de cada token
 │ :8081  ESCRITURA    │        │ servicio-consultas  │
 │ reglas + permisos   │        │ :8082  LECTURA      │       APLICACIÓN SERVIDOR
 │ PostgreSQL :5434    │        │ MongoDB :27018 + SSE│
 └─────────┬───────────┘        └───────▲─────────────┘
           │ publica (después del commit)│ consume (grupos consultas-*)
           ▼                             │
     ┌──────────────── KAFKA :9094 ──────┴──────────┐   PILA DE MENSAJERÍA
     │  calzado-eventos (3 part.)  pedido-eventos (3)│
     └──────────────────────────────┬───────────────┘
                                    ▼ consume (grupo notificaciones)
                         ┌─────────────────────────┐
                         │ servicio-notificaciones │  correos simulados + alertas de stock
                         └─────────────────────────┘
```

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
| `tienda_comandos` | PostgreSQL | servicio-comandos | **Fuente de verdad**: tablas `calzados` y `pedidos` con transacciones, llave foránea y `@Version` |
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
    W->>PG: INSERT pedido + UPDATE stock (una transacción)
    W-->>C: 201 {id, mensaje}
    W->>K: después del COMMIT: PEDIDO_CREADO y STOCK_DESCONTADO
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
| Ver mis pedidos / recibirlos en vivo | ❌ 401 | ✅ solo los suyos | ✅ |
| Cancelar un pedido | ❌ 401 | ✅ solo los suyos (si no, 403) | ✅ cualquiera |
| Ver todos los pedidos | ❌ 401 | ❌ 403 | ✅ |
| Registrar calzado, cambiar precio, reponer stock | ❌ 401 | ❌ 403 | ✅ |
| Despachar pedidos | ❌ 401 | ❌ 403 | ✅ |
| Reconstruir el modelo de lectura | ❌ 401 | ❌ 403 | ✅ |
| Abrir `admin.html` | → login | → login | ✅ |

- **401 Unauthorized** = no sé quién eres (sin token, vencido o alterado).
- **403 Forbidden** = sé quién eres, pero no tienes permiso.
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

1. Descarga el proyecto y levanta la infraestructura (PostgreSQL, MongoDB, Kafka y Kafka UI):
   ```powershell
   git clone https://github.com/JNoxg8/proyecto-CQRS.git
   cd proyecto-CQRS
   docker compose up -d
   ```
   La primera vez, `docker/postgres-init/01-bases.sql` crea la base `tienda_usuarios`. Si tu volumen de Postgres
   ya existía antes de agregar la autenticación, créala una vez a mano:
   ```powershell
   docker exec tienda-postgres psql -U tienda -d tienda_comandos -c "CREATE DATABASE tienda_usuarios OWNER tienda;"
   ```
2. En IntelliJ: **File → Open** esta carpeta (proyecto Maven), SDK = JDK 21, y ejecuta
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
3. Abre <http://localhost:8080> (la tienda). El panel de admin está en <http://localhost:8080/admin.html> y pide
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
| GET | `/api/stream` | Canal en vivo (SSE), filtrado según quién eres | público / con token |
| GET | `/api/pedidos/mios` | Mis pedidos | autenticado |
| GET | `/api/pedidos`, `/api/pedidos/{id}` | Todos los pedidos / uno | ADMIN |
| POST | `/api/admin/reconstruir` | Borra MongoDB y reproduce todos los eventos de Kafka | ADMIN |

Errores con formato *Problem Details* (RFC 7807): `400` datos inválidos, `401` no autenticado, `403` sin permiso,
`404` no existe, `409` regla de negocio o conflicto de concurrencia.

Pruebas listas en `requests.http` (IntelliJ: guarda los tokens solo) y `tienda_talend_collection.json`
(Talend: haz el login, copia el `token` y pégalo en la cabecera `Authorization` de las demás peticiones).

## Eventos

| Tópico (clave) | Eventos |
|---|---|
| `calzado-eventos` (calzadoId) | `CALZADO_REGISTRADO`, `PRECIO_CAMBIADO`, `STOCK_REPUESTO`, `STOCK_DESCONTADO`, `STOCK_DEVUELTO` |
| `pedido-eventos` (pedidoId) | `PEDIDO_CREADO`, `PEDIDO_DESPACHADO`, `PEDIDO_CANCELADO` |

Cada evento lleva el **estado completo** de la entidad (*event-carried state transfer*), por eso la proyección
es un simple *upsert* idempotente. Por el canal en vivo, los eventos de calzado llegan a todos, pero un evento de
pedido (con nombre y correo) solo llega al ADMIN y al dueño del pedido.

## Guion de demostración (6 minutos)

1. Abre la **Tienda** sin iniciar sesión: el catálogo se ve, pero comprar pide iniciar sesión.
2. Intenta abrir `admin.html`: te lleva al login con el aviso "solo para administradores".
3. En una ventana inicia sesión como **cliente**; en otra ventana (o pestaña), como **admin**. Ten a la vista la
   consola de `servicio-notificaciones`.
4. Compra como cliente. Observa:
   - el aviso "*Pedido #N ya está en MongoDB X ms después*" → **consistencia eventual**;
   - el Admin se actualiza solo (SSE) y aparece el `[CORREO SIMULADO]` → **pub/sub**.
5. Crea una segunda cuenta de cliente y compra: el primer cliente **no** ve ese pedido (ni en la tabla ni en vivo).
6. Desde Talend, con el token del cliente, intenta registrar un calzado → **403**; sin token → **401**.
7. En el Admin **cambia un precio**: los pedidos viejos conservan su precio (**modelo desnormalizado**).
8. Pulsa **Reconstruir modelo de lectura**: MongoDB se vacía y se reconstruye desde Kafka (**event replay**).
9. Muestra en pgAdmin `tienda_usuarios.usuarios`: las contraseñas están como hash BCrypt (`$2a$10$...`).

## Decisiones de diseño

- **Comandos con intención** (`CrearPedido`, `ReponerStock`) en vez de CRUD genérico; los comandos no devuelven datos.
- **Reglas de negocio dentro de las entidades** (`Calzado.descontarStock`, `Pedido.cancelar`), probadas con JUnit.
- **`@Version` (bloqueo optimista)**: dos compras simultáneas del último par no pueden vender de más.
- **Publicación después del commit** (`@TransactionalEventListener(AFTER_COMMIT)`): Kafka nunca anuncia algo que se revirtió.
- **Clave del mensaje = id** de la entidad: los eventos de un mismo calzado llegan en orden aunque haya 3 particiones.
- **`ErrorHandlingDeserializer`**: un mensaje corrupto no bloquea al consumidor para siempre.
- **Seguridad**: JWT RS256 (solo autenticación puede firmar), BCrypt, mismo mensaje para correo inexistente y clave
  incorrecta (evita enumerar usuarios), lista blanca de rutas (`denyAll` por defecto), CORS limitado a
  `localhost:8080`, escape de HTML contra XSS, validación con Bean Validation, base de datos separada para usuarios.

### Límites conocidos (mejoras posibles)

- Si Kafka cae justo después del commit, el evento se pierde → **Transactional Outbox**.
- Un JWT no se puede revocar al instante: sigue valiendo hasta que vence (2 h) → expiración corta + *refresh tokens*.
- El token en `sessionStorage` es legible por JavaScript: un XSS podría robarlo → cookies `HttpOnly` con un BFF.
- No hay límite de intentos de login → *rate limiting* / bloqueo temporal.
- La clave RSA se genera al arrancar; en producción vendría de un almacén de secretos (*vault*).
- Kafka UI no tiene autenticación: es una herramienta interna que no debe exponerse.
- Un mensaje que falla siempre se descarta tras los reintentos → **Dead Letter Topic**.

## Estructura del repositorio

```
proyecto-CQRS/
├── docker-compose.yml           # PostgreSQL :5434, MongoDB :27018, Kafka :9094, Kafka UI :8090
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
35 pruebas: reglas de negocio, proyecciones, login/registro/firma de tokens y la **matriz de permisos** de cada API
(con tokens simulados). No necesitan Docker.
