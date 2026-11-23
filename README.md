# Proyecto CQRS · Tienda de calzado con Kafka

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.8-231F20)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-escritura-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-lectura-47A248)
![Pruebas](https://img.shields.io/badge/pruebas-38%20automáticas-blue)

Proyecto académico — **Universidad Central**.

**Integrantes:** Juan Pablo Garcia · Kevin Estupinan Sotelo

## ¿Qué es este proyecto?

Es una **tienda de zapatos en línea**. Los compradores ven el catálogo y compran, y un administrador agrega zapatos,
cambia precios y despacha los pedidos.

Lo interesante está en **cómo funciona por dentro**: el sistema separa en dos partes el trabajo de **guardar**
(vender, cambiar un precio) y el de **mostrar** (el catálogo, la lista de pedidos). Cada parte tiene su propia base de
datos, y se mantienen al día gracias a **avisos** que viajan por un sistema de mensajería llamado **Kafka**.

| Lo que pedía el trabajo | Cómo se resolvió |
|---|---|
| Un sistema de mensajería | **Kafka**, que reparte los avisos entre las partes del sistema |
| El patrón **CQRS** | Una parte que solo **guarda** cambios y otra que solo **muestra** datos, cada una con su base de datos |
| Una aplicación servidor | 4 programas en Java que trabajan juntos (acceso, escritura, lectura y notificaciones) |
| Aplicaciones cliente | 3 páginas web: Tienda, Panel de administración e Inicio de sesión, que se actualizan solas |

Además, el proyecto tiene:
- **Seguridad:** hay que iniciar sesión para comprar, y solo el administrador puede entrar al panel de administración.
- **Resistencia a fallos:** si una pieza se cae, la tienda sigue vendiendo y no se pierde ninguna venta.

📄 Documentación en Word (carpeta [`docs/`](docs/)):
- [Documentación del proyecto](docs/Documentacion_Proyecto_CQRS.docx): el documento completo para el curso.
- [Guía de ejecución y demostración](docs/Guia_de_ejecucion_y_demostracion.docx): qué hacer paso a paso y dónde se
  ve cada resultado.

## Contenido

- [Una comparación: el restaurante](#una-comparación-el-restaurante)
- [Palabras clave](#palabras-clave)
- [Cómo funciona](#cómo-funciona)
- [Una compra, paso a paso](#una-compra-paso-a-paso)
- [Quién puede hacer qué](#quién-puede-hacer-qué)
- [Qué pasa si algo falla](#qué-pasa-si-algo-falla)
- [Cómo ejecutarlo](#cómo-ejecutarlo)
- [Guion de demostración](#guion-de-demostración)
- [Detalles técnicos (para desarrolladores)](#detalles-técnicos-para-desarrolladores)

## Una comparación: el restaurante

| En un restaurante | En este proyecto |
|---|---|
| El mesero toma el pedido y lo anota en la **libreta oficial** | El **servicio de escritura** guarda la venta en la **base de datos principal** |
| Pega la comanda en la **ventanilla** de la cocina | El aviso se publica en **Kafka** |
| La cocina, la caja y el bar leen **la misma comanda**, cada uno por su cuenta | Los servicios de **lectura** y de **notificaciones** reciben el mismo aviso |
| La **pizarra del menú** muestra lo que hay disponible | La **base de datos de lectura** muestra el catálogo |
| Si la ventanilla está bloqueada, el mesero deja la comanda **marcada como pendiente** y la pega apenas puede | Si Kafka está caído, el aviso queda en una **bandeja de salida** y se envía cuando vuelve |

El mesero no va a la cocina a explicar cada pedido: lo pega y sigue atendiendo. Así, si la cocina se demora, el
mesero no se detiene. Esa independencia entre partes es la idea central del proyecto.

## Palabras clave

| Palabra | Qué significa, en simple |
|---|---|
| **CQRS** | Separar el trabajo de **guardar** cambios del trabajo de **mostrar** datos. Viene del inglés *Command Query Responsibility Segregation*. |
| **Comando** | Una orden que cambia algo: "comprar", "cambiar precio", "despachar". |
| **Consulta** | Una pregunta que solo lee: "¿qué zapatos hay?", "¿cuáles son mis pedidos?". |
| **Evento (aviso)** | Un mensaje que cuenta algo que ya pasó: "se creó el pedido 12", "bajó el stock del zapato 3". |
| **Kafka** | El sistema que recibe los avisos y se los reparte a quien los necesite. Además guarda el historial completo. |
| **Servicio** | Un programa independiente que hace una sola tarea (por ejemplo, el de lectura). |
| **Pase digital (token)** | Lo que recibe el usuario al iniciar sesión. Dice quién es y qué puede hacer; nadie puede falsificarlo. |
| **Bandeja de salida (outbox)** | Donde quedan guardados los avisos hasta que Kafka confirma que los recibió. |
| **Consistencia eventual** | La copia de lectura se pone al día un instante después (milisegundos) de cada venta. |
| **Actualización en vivo (SSE)** | La página se actualiza sola, sin recargar, cuando algo cambia. |
| **Contenedor (Docker)** | Una "caja" que trae lista una base de datos o Kafka, para no tener que instalarlos a mano. |

## Cómo funciona

El diagrama sigue **una compra de arriba hacia abajo**. Los colores separan las dos mitades del sistema:
🟧 la que **guarda** y 🟩 la que **muestra**, unidas solo por ⬛ **Kafka**.

```mermaid
flowchart TB
    subgraph CLI["🖥️ PÁGINAS WEB"]
        LOGIN["🔑 <b>Iniciar sesión</b>"]
        TIENDA["🛒 <b>Tienda</b><br/>para compradores"]
        ADMIN["🛠️ <b>Panel de administración</b><br/>solo administradores"]
    end

    AUTH["🔐 <b>Servicio de acceso</b><br/>revisa la contraseña y<br/>entrega un pase digital<br/><i>servicio-autenticacion</i>"]
    CMD["✍️ <b>Servicio de escritura</b><br/>revisa permisos y que haya stock<br/><i>servicio-comandos</i>"]
    PG[("<b>Base de datos principal</b><br/>PostgreSQL<br/>registro oficial de ventas<br/>+ bandeja de avisos por enviar")]
    KAFKA{{"📨 <b>Kafka</b><br/>cartelera de avisos<br/>(mensajería)"}}
    QRY["👀 <b>Servicio de lectura</b><br/>muestra catálogo y pedidos<br/>y avisa los cambios al instante<br/><i>servicio-consultas</i>"]
    NOTIF["✉️ <b>Servicio de notificaciones</b><br/>correos (simulados) y<br/>alertas de poco stock"]
    MONGO[("<b>Base de datos de lectura</b><br/>MongoDB<br/>copia lista para mostrar")]

    LOGIN -->|"① inicia sesión y recibe su pase"| AUTH
    TIENDA -->|"② compra, mostrando su pase"| CMD
    ADMIN -->|"② agrega zapatos, cambia precios, despacha"| CMD
    CMD -->|"③ guarda la venta y su aviso, juntos"| PG
    PG -->|"④ se envía el aviso"| KAFKA
    KAFKA -->|"⑤ reparte el aviso"| QRY
    KAFKA -->|"⑤ reparte el mismo aviso"| NOTIF
    QRY -->|"⑥ actualiza la copia"| MONGO
    TIENDA -.->|"⑦ mira catálogo y pedidos al instante"| QRY
    ADMIN -.->|"⑦ mira todo al instante"| QRY

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

1. El usuario **inicia sesión**. El servicio de acceso revisa su contraseña y le entrega un **pase digital**, que
   dice quién es y si es comprador o administrador.
2. Desde la **Tienda** o el **Panel de administración**, el usuario da una orden (comprar, cambiar un precio…) y
   muestra su pase.
3. El **servicio de escritura** revisa que tenga permiso y que haya stock. Luego guarda la venta **y el aviso** de lo
   que pasó **al mismo tiempo**: o se guardan los dos, o ninguno.
4. Cada segundo, los avisos pendientes se envían a **Kafka**.
5. Kafka **reparte el mismo aviso** a cada servicio interesado, y cada uno lo procesa por su cuenta.
6. El **servicio de lectura** actualiza su copia de los datos, preparada para mostrarse rápido.
7. Las páginas **leen** solo del servicio de lectura (líneas punteadas) y se actualizan solas cuando algo cambia.

> **Lo más importante:** no hay ninguna flecha entre el servicio de **escritura** y el de **lectura**. Solo se
> comunican por medio de Kafka. Por eso, si la parte de lectura se cae, se sigue vendiendo.

### ¿Por qué dos bases de datos?

Porque guardar y mostrar necesitan cosas distintas:

| Base de datos | Para qué se usa | Por qué esa |
|---|---|---|
| **Principal** (PostgreSQL) | Guardar ventas, stock y los avisos por enviar. Es el **registro oficial**. | Es muy estricta: nunca deja vender un par que no existe ni dejar datos a medias. |
| **De lectura** (MongoDB) | Mostrar el catálogo y los pedidos. Es una **copia**. | Guarda los datos ya listos para mostrar, así que responde rápido. |
| **De usuarios** (PostgreSQL) | Guardar las cuentas. Las contraseñas no se guardan tal cual, sino transformadas para que nadie pueda leerlas. | Se mantiene separada del resto por seguridad. |

Cada servicio usa **solo su propia** base de datos; ninguno entra a la de otro.

## Una compra, paso a paso

```mermaid
sequenceDiagram
    actor C as Comprador (Tienda)
    participant A as Servicio de acceso
    participant W as Servicio de escritura
    participant PG as Base principal
    participant K as Kafka
    participant R as Servicio de lectura
    participant M as Base de lectura
    participant N as Servicio de notificaciones
    actor AD as Administrador

    C->>A: Inicia sesión (correo y contraseña)
    A-->>C: Le entrega su pase digital
    C->>W: "Quiero este zapato" + su pase
    W->>W: ¿El pase es válido? ¿Hay stock?
    W->>PG: Guarda la venta, descuenta el stock y deja el aviso pendiente (todo junto)
    W-->>C: "Pedido recibido"
    W->>K: Envía los avisos pendientes (cada segundo)
    W->>PG: Marca los avisos como enviados
    K->>R: Le entrega el aviso
    R->>M: Actualiza la copia
    R-->>C: Su pedido aparece en pantalla
    R-->>AD: El pedido nuevo aparece en su panel
    K->>N: Le entrega el mismo aviso
    N->>N: "Envía" el correo y avisa si queda poco stock
```

El "Pedido recibido" llega **antes** de que se actualice la copia de lectura: hay unos milisegundos de diferencia. A
eso se le llama **consistencia eventual**, y la tienda muestra en pantalla cuánto tardó.

## Quién puede hacer qué

| Acción | Visitante (sin sesión) | Comprador | Administrador |
|---|:-:|:-:|:-:|
| Ver el catálogo (y sus cambios en vivo) | ✅ | ✅ | ✅ |
| Comprar | ❌ | ✅ | ✅ |
| Ver los propios pedidos | ❌ | ✅ solo los suyos | ✅ |
| Cancelar un pedido | ❌ | ✅ solo los suyos | ✅ cualquiera |
| Ver los pedidos de todos | ❌ | ❌ | ✅ |
| Agregar zapatos, cambiar precios, reponer stock | ❌ | ❌ | ✅ |
| Despachar pedidos | ❌ | ❌ | ✅ |
| Reconstruir la copia de lectura | ❌ | ❌ | ✅ |
| Entrar al panel de administración | lo manda a iniciar sesión | lo manda a iniciar sesión | ✅ |

- Los permisos se revisan **en el servidor**. Esconder un botón en la página no protege nada, porque cualquiera podría
  enviar la orden por otro camino.
- Quien se registra en la página siempre queda como **comprador**. Los administradores solo se crean desde la
  configuración del servidor.
- **Cuentas de prueba:** están en `servicio-autenticacion/src/main/resources/application.yml`, en
  `tienda.usuarios-iniciales` (un administrador y un comprador).

### Cómo funciona el pase digital

Funciona como la **manilla de un concierto**:

1. La **taquilla** (servicio de acceso) revisa la contraseña y entrega la manilla con un **sello** que solo ella sabe
   hacer. La manilla dice el nombre, el tipo de usuario y vence a las **2 horas**.
2. El navegador la guarda mientras la pestaña está abierta y la muestra en cada orden.
3. Los demás servicios **revisan el sello** sin tener que llamar a la taquilla. Si alguien altera la manilla (por
   ejemplo, se cambia a "administrador"), el sello deja de coincidir y la orden se rechaza.
4. El servidor siempre saca **quién es el usuario de la manilla**, nunca de lo que escriba en la página. Así nadie
   puede ver los pedidos de otro cambiando un correo.

## Qué pasa si algo falla

| Si falla… | Qué pasa |
|---|---|
| **Kafka** | **Se sigue vendiendo.** Los avisos esperan en la bandeja de salida de la base principal y se envían, en orden, cuando Kafka vuelve. |
| **La base de datos de lectura** | **Se sigue vendiendo.** El servicio de lectura vuelve a intentarlo, esperando cada vez un poco más (1, 2, 4, 8, 10 segundos…) durante unos 2 minutos. Si la base vuelve, no se pierde nada. |
| **Llega un aviso dañado** | No frena a los demás ni se pierde: se aparta en una **bandeja especial de mensajes con problemas** para revisarlo después. |
| **El servicio de lectura o el de notificaciones se apaga** | La parte de escritura ni se entera. Kafka guarda los avisos y, al volver, cada servicio sigue donde quedó. |
| **Se pierde toda la copia de lectura** | El administrador pulsa **Reconstruir** y la copia se vuelve a armar desde el historial de Kafka, que no borra los avisos. |
| **El servicio de escritura se apaga en medio de un envío** | Al volver, reenvía lo que no alcanzó a enviar. Un aviso puede llegar dos veces, pero aplicarlo dos veces da el mismo resultado. |
| **Dos personas compran el último par al mismo tiempo** | Solo una compra se acepta; la otra recibe un mensaje de que no se pudo. Nunca se vende de más. |
| **Se cae un contenedor** | Docker lo vuelve a encender solo, y los datos no se pierden porque se guardan fuera del contenedor. |

**¿Por qué la bandeja de salida?** Antes, la venta se guardaba primero y el aviso se enviaba después. Si Kafka estaba
caído justo en ese momento, la venta quedaba guardada pero **el aviso se perdía**, y la tienda mostraba datos
equivocados para siempre. Ahora el aviso se guarda **junto con la venta**, en la base principal, y se envía apenas
Kafka lo puede recibir. Solo se marca como enviado cuando Kafka confirma que lo tiene.

**Cómo demostrarlo** (con todo encendido), en PowerShell:

```powershell
# 1) Kafka caído: se sigue vendiendo
docker stop tienda-kafka
#    → se compra en la tienda: "Pedido #N aceptado... Esperando que llegue al servicio de CONSULTAS"
docker exec tienda-postgres psql -U tienda -d tienda_comandos -c "select id, tipo, enviado_en from outbox_eventos order by id desc limit 4;"
#    → los avisos nuevos tienen "enviado_en" vacío: están pendientes
docker start tienda-kafka
#    → a los pocos segundos el pedido aparece solo en la tienda

# 2) Base de lectura caída: el servicio vuelve a intentar
docker stop tienda-mongo
#    → se compra; la consola de consultas muestra "Fallo procesando ... (intento N). Se reintentara con espera."
docker start tienda-mongo
#    → el pedido aparece sin perder nada

# 3) Aviso dañado → bandeja de mensajes con problemas
docker exec tienda-kafka sh -c 'echo prueba:esto-no-es-json | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9094 --topic calzado-eventos --property parse.key=true --property key.separator=:'
#    → en Kafka UI (http://localhost:8090) aparece en "calzado-eventos.DLT"
```

## Cómo ejecutarlo

Se necesita: **Git**, **Docker Desktop** abierto y **Java 21** (JDK).

1. Se descarga el proyecto y se encienden las bases de datos y Kafka:
   ```powershell
   git clone https://github.com/JNoxg8/proyecto-CQRS.git
   cd proyecto-CQRS
   docker compose up -d
   ```
   Si la base de datos ya existía de una versión anterior del proyecto, la base de usuarios se crea una sola vez así:
   ```powershell
   docker exec tienda-postgres psql -U tienda -d tienda_comandos -c "CREATE DATABASE tienda_usuarios OWNER tienda;"
   ```
2. En IntelliJ: **File → Open** con esta carpeta, se elige Java 21 y se ejecuta
   **`Tienda: TODO (servidor + clientes)`**, que enciende todos los servicios a la vez.

   Sin IntelliJ (una terminal por cada servicio):
   ```powershell
   .\mvnw.cmd -DskipTests package
   java -jar servicio-autenticacion\target\servicio-autenticacion-1.0.0.jar
   java -jar servicio-comandos\target\servicio-comandos-1.0.0.jar
   java -jar servicio-consultas\target\servicio-consultas-1.0.0.jar
   java -jar servicio-notificaciones\target\servicio-notificaciones-1.0.0.jar
   java -jar cliente-web\target\cliente-web-1.0.0.jar
   ```
3. Se abre <http://localhost:8080> en el navegador.

| Qué | Dirección | Usuario / contraseña |
|---|---|---|
| **Tienda** | http://localhost:8080 | cuentas de prueba (ver arriba) |
| **Panel de administración** | http://localhost:8080/admin.html | cuenta de administrador |
| Kafka UI (ver los avisos) | http://localhost:8090 | — |
| Base principal (pgAdmin) | localhost:**5434**, bases `tienda_comandos` y `tienda_usuarios` | `tienda` / `tienda123` |
| Base de lectura (Compass) | `mongodb://admin:admin@localhost:27018/?authSource=admin`, base `tienda_consultas` | `admin` / `admin` |

## Guion de demostración

1. Se abre la **Tienda** sin iniciar sesión: el catálogo se ve, pero para comprar pide iniciar sesión.
2. Se intenta abrir el panel de administración: la página manda a iniciar sesión con el aviso "solo para
   administradores".
3. Se inicia sesión como **comprador** en una ventana y como **administrador** en otra, con la consola del servicio
   de notificaciones a la vista.
4. El comprador hace una compra. Se observa:
   - el mensaje "*Pedido #N ya está en MongoDB X ms después*": la copia de lectura se puso al día en milisegundos;
   - el panel del administrador se actualiza solo y aparece el correo simulado: **un mismo aviso llegó a dos
     servicios distintos**.
5. Se crea otra cuenta de comprador y se compra: el primer comprador **no** ve ese pedido.
6. Con la cuenta de comprador se intenta agregar un zapato: el servidor lo **rechaza por falta de permiso**.
7. El administrador **cambia un precio**: los pedidos anteriores conservan el precio con el que se compraron.
8. Se pulsa **Reconstruir**: la copia de lectura se borra y se vuelve a armar desde el historial de Kafka.
9. En pgAdmin se ve que las contraseñas no están guardadas tal cual, sino transformadas (`$2a$10$...`).
10. **Resistencia a fallos:** se apaga Kafka, se compra (funciona), se muestra el aviso pendiente, se enciende Kafka
    y el pedido aparece solo (ver [Qué pasa si algo falla](#qué-pasa-si-algo-falla)).

---

## Detalles técnicos (para desarrolladores)

Esta sección es para quien quiera revisar el código, así que usa los nombres técnicos.

### Módulos

| Módulo | Qué hace | Tecnología |
|---|---|---|
| `eventos` | Formato común de los avisos (`CalzadoEvento`, `PedidoEvento`) y nombres de los tópicos | Java |
| `servicio-autenticacion` (:8083) | Registro, login, contraseñas con BCrypt y emisión de tokens JWT firmados (RS256) | Spring Security, JPA, PostgreSQL |
| `servicio-comandos` (:8081) | Recibe las órdenes, revisa permisos y reglas, guarda y publica eventos | Spring Web, JPA, PostgreSQL, Kafka |
| `servicio-consultas` (:8082) | Aplica los eventos en MongoDB, responde las lecturas y envía cambios en vivo (SSE) | Spring Web, MongoDB, Kafka |
| `servicio-notificaciones` | Segundo consumidor de los mismos eventos (otro *consumer group*) | Spring Kafka |
| `cliente-web` (:8080) | Sirve las páginas Tienda, Admin y Login | HTML, CSS, JavaScript |

Bases de datos: `tienda_comandos` (PostgreSQL: tablas `calzados`, `pedidos` y `outbox_eventos`),
`tienda_consultas` (MongoDB: colecciones `catalogo` y `pedidos`) y `tienda_usuarios` (PostgreSQL: tabla `usuarios`).

### API

**Autenticación** (`servicio-autenticacion`):

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/auth/registro` | Crea una cuenta de comprador y devuelve su token (`{nombre, email, password}`) |
| POST | `/api/auth/login` | Devuelve `{token, expiraEn, nombre, email, rol}` |
| GET | `/.well-known/jwks.json` | Clave pública para verificar los tokens |

**Comandos** (`servicio-comandos`, solo escriben; devuelven `{id, mensaje}`):

| Método | Ruta | Qué hace | Quién |
|---|---|---|---|
| POST | `/api/pedidos` | Crear pedido `{calzadoId, cantidad}` | comprador, admin |
| POST | `/api/pedidos/{id}/cancelar` | Cancelar y devolver el stock (solo si está CREADO) | dueño o admin |
| POST | `/api/pedidos/{id}/despachar` | Despachar (solo si está CREADO) | admin |
| POST | `/api/calzados` | Registrar un zapato | admin |
| PUT | `/api/calzados/{id}/precio` | Cambiar precio | admin |
| POST | `/api/calzados/{id}/reponer-stock` | Reponer stock | admin |

**Consultas** (`servicio-consultas`, solo leen):

| Método | Ruta | Qué devuelve | Quién |
|---|---|---|---|
| GET | `/api/catalogo?marca=&categoria=&talla=&precioMax=&soloDisponibles=` | Catálogo filtrado | todos |
| GET | `/api/catalogo/{id}` | Un zapato | todos |
| GET | `/api/stream` | Canal de cambios en vivo (SSE), filtrado según el usuario | todos / con token |
| GET | `/api/pedidos/mios` | Pedidos del usuario | con sesión |
| GET | `/api/pedidos`, `/api/pedidos/{id}` | Todos los pedidos / uno | admin |
| POST | `/api/admin/reconstruir` | Borra MongoDB y vuelve a aplicar todos los eventos de Kafka | admin |

Códigos de respuesta: `400` datos inválidos, `401` sin sesión o token inválido, `403` sin permiso, `404` no existe,
`409` sin stock o compra simultánea. Hay peticiones de prueba listas en `requests.http` (IntelliJ) y
`tienda_talend_collection.json` (Talend API Tester).

### Eventos

| Tópico (clave) | Eventos |
|---|---|
| `calzado-eventos` (calzadoId) | `CALZADO_REGISTRADO`, `PRECIO_CAMBIADO`, `STOCK_REPUESTO`, `STOCK_DESCONTADO`, `STOCK_DEVUELTO` |
| `pedido-eventos` (pedidoId) | `PEDIDO_CREADO`, `PEDIDO_DESPACHADO`, `PEDIDO_CANCELADO` |
| `calzado-eventos.DLT`, `pedido-eventos.DLT` | *Dead Letter Topics*: mensajes que no se pudieron procesar |

- Los tópicos tienen **retención infinita** (`retention.ms=-1`), porque son el historial con el que se reconstruye la
  base de lectura.
- Cada evento lleva el **estado completo** del zapato o pedido, así que aplicarlo dos veces da el mismo resultado
  (*upsert* idempotente).
- La clave del mensaje es el id: los eventos de un mismo zapato o pedido llegan en orden aunque haya 3 particiones.

### Mecanismos de resistencia a fallos

| Falla | Mecanismo | Dónde está |
|---|---|---|
| Kafka caído | *Transactional Outbox*: el evento se guarda en la misma transacción que la venta; un relevo lo envía cada segundo y espera la confirmación (`acks=all`, productor idempotente) | `RegistradorOutbox`, `RelevoOutbox` |
| MongoDB caído | Reintentos con espera exponencial (1 s × 2, máx. 10 s, ~2 min) | `DefaultErrorHandler` en `ErroresKafkaConfig` |
| Mensaje corrupto | *Dead Letter Topic* con cabeceras de la excepción, el *offset* y el grupo | `DeadLetterPublishingRecoverer`, `ErrorHandlingDeserializer` |
| Consumidor apagado | Cada *consumer group* retoma desde su último *offset* confirmado | Kafka |
| Se pierde MongoDB | Reproducción de eventos desde el *offset* 0 | `POST /api/admin/reconstruir` |
| Duplicados tras una caída | Entrega *al menos una vez* + proyecciones idempotentes | `servicio-consultas` |
| Compra simultánea del último par | Bloqueo optimista | `@Version` en `Calzado` |
| Contenedor caído | `restart: unless-stopped`, `healthcheck`, volúmenes con nombre | `docker-compose.yml` |

### Decisiones de diseño

- **Órdenes con intención** (`CrearPedido`, `ReponerStock`) en vez de un CRUD genérico; las órdenes no devuelven datos.
- **Reglas de negocio dentro de las entidades** (`Calzado.descontarStock`, `Pedido.cancelar`), probadas con JUnit.
- **Seguridad:** JWT RS256 (solo el servicio de acceso puede firmar), BCrypt, el mismo mensaje para correo inexistente
  y contraseña incorrecta, todas las rutas cerradas salvo las permitidas, CORS limitado a `localhost:8080`,
  protección contra XSS y base de datos separada para los usuarios.

### Límites conocidos (mejoras posibles)

- Kafka corre en **un solo servidor**: si su disco se daña, se pierde el historial. En producción serían 3 servidores
  con copias de cada mensaje.
- El envío de avisos pendientes supone **una sola copia** del servicio de escritura; con varias haría falta un bloqueo
  (`SELECT ... FOR UPDATE SKIP LOCKED`) o una herramienta como Debezium.
- Tras una caída, el servicio de notificaciones podría repetir un correo simulado.
- Un token no se puede anular antes de que venza (2 horas).
- El token se guarda en el navegador, donde un script malicioso (XSS) podría leerlo; lo ideal serían cookies `HttpOnly`.
- No hay límite de intentos de inicio de sesión.
- La clave para firmar tokens se genera al arrancar; en producción vendría de un almacén de secretos.
- Kafka UI no pide contraseña: es una herramienta interna que no debe publicarse.

### Estructura del repositorio

```
proyecto-CQRS/
├── docker-compose.yml           # PostgreSQL :5434, MongoDB :27018, Kafka :9094, Kafka UI :8090
├── docker/postgres-init/        # crea la base tienda_usuarios la primera vez
├── pom.xml                      # proyecto Maven principal
├── eventos/                     # formato común de los avisos
├── servicio-autenticacion/      # :8083 inicio de sesión y tokens
├── servicio-comandos/           # :8081 parte de ESCRITURA
├── servicio-consultas/          # :8082 parte de LECTURA
├── servicio-notificaciones/     # correos simulados y alertas de stock
├── cliente-web/                 # :8080 páginas web (tienda.html, admin.html, login.html)
├── .run/                        # configuraciones de IntelliJ ("Tienda: TODO")
├── requests.http                # peticiones de prueba para IntelliJ
├── tienda_talend_collection.json# las mismas peticiones para Talend API Tester
└── docs/                        # documentación en Word
```

Cada servicio se organiza en capas: `api` (recibe las peticiones) → `aplicacion` (casos de uso) → `dominio` (reglas
de negocio) → `repositorio` (acceso a la base de datos), más `mensajeria`/`proyeccion` para Kafka y `config`.

### Pruebas

```powershell
.\mvnw.cmd test
```

38 pruebas automáticas: reglas de negocio, actualización de la copia de lectura, inicio de sesión y tokens, permisos
de cada ruta y la bandeja de salida (qué se guarda, en qué orden se envía y que nada se salta si Kafka está caído).
No necesitan Docker.
