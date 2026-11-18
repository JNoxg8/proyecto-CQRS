/*
 * Shared code of the client apps (Tienda, Admin and Login).
 *
 *   Login / sign-up   -> servicio-autenticacion  http://localhost:8083/api/auth
 *   Commands (write)  -> servicio-comandos       http://localhost:8081/api
 *   Queries  (read)   -> servicio-consultas      http://localhost:8082/api
 *   Live events (SSE) -> servicio-consultas      http://localhost:8082/api/stream
 */
const API = {
  autenticacion: 'http://localhost:8083/api/auth',
  comandos: 'http://localhost:8081/api',
  consultas: 'http://localhost:8082/api',
};

const $ = (selector) => document.querySelector(selector);

// ---------------------------------------------------------------- session (JWT)

/*
 * The session lives in sessionStorage: it is PER TAB and disappears when the tab is closed.
 * That lets you have a customer in one window and the admin in another during the demo.
 * Note: any script running on the page can read it, which is why escaping HTML (anti-XSS) matters so much.
 */
const CLAVE_SESION = 'tienda.sesion';

/** {token, expiraEn, nombre, email, rol} or null if there is no valid session. */
function obtenerSesion() {
  try {
    const sesion = JSON.parse(sessionStorage.getItem(CLAVE_SESION));
    if (!sesion) return null;
    if (Date.parse(sesion.expiraEn) <= Date.now()) {   // expired token: treat it as logged out
      cerrarSesion();
      return null;
    }
    return sesion;
  } catch {
    return null;
  }
}

/** Stores the session returned by servicio-autenticacion (login / sign-up). */
function guardarSesion(sesion) {
  try {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  } catch { /* storage blocked: the user will have to log in again on each page */ }
}

/** Logs out locally (the token is simply forgotten; it expires by itself). */
function cerrarSesion() {
  try {
    sessionStorage.removeItem(CLAVE_SESION);
  } catch { /* ignore */ }
}

/** true if the session belongs to an administrator (only for the UI). */
const esAdmin = (sesion) => sesion?.rol === 'ADMIN';

/** Goes to the login page and comes back to the current page afterwards. */
function irALogin(motivo) {
  const pagina = location.pathname.split('/').pop() || 'tienda.html';
  const parametros = new URLSearchParams({ volver: pagina });
  if (motivo) parametros.set('motivo', motivo);
  location.href = 'login.html?' + parametros;
}

/** {Authorization: "Bearer <token>"} if there is a session, {} otherwise. */
function cabeceraAutorizacion() {
  const sesion = obtenerSesion();
  return sesion ? { Authorization: 'Bearer ' + sesion.token } : {};
}

/** Fills the session area of the header: "Iniciar sesión" or "Hola, Ana · Salir". */
function pintarSesion() {
  const zona = $('#sesion');
  if (!zona) return;
  const sesion = obtenerSesion();
  if (!sesion) {
    zona.innerHTML = '<a class="boton-enlace" href="#" id="ir-login">Iniciar sesión</a>';
    $('#ir-login').addEventListener('click', (e) => { e.preventDefault(); irALogin(); });
    return;
  }
  zona.innerHTML = `
    <span class="usuario">${escapar(sesion.nombre)} <span class="rol ${escapar(sesion.rol).toLowerCase()}">${escapar(sesion.rol)}</span></span>
    <button class="secundario pequeno" id="salir">Salir</button>`;
  $('#salir').addEventListener('click', () => {
    cerrarSesion();
    location.href = 'tienda.html';
  });
}

// ---------------------------------------------------------------- HTTP

/** Turns an HTTP response into data, or into an Error with a message for the user. */
async function leerRespuesta(respuesta) {
  const datos = await respuesta.json().catch(() => ({}));
  if (respuesta.status === 401) {
    // Missing, expired or invalid token (e.g. servicio-autenticacion restarted with a new key)
    cerrarSesion();
    irALogin('expirada');
    throw new Error('Tu sesión expiró. Inicia sesión de nuevo.');
  }
  if (respuesta.status === 403) {
    throw new Error(datos.detail || 'No tienes permiso para hacer esto.');
  }
  if (!respuesta.ok) {
    // The servers answer errors as Problem Details (RFC 7807): {title, detail, status}
    throw new Error(datos.detail || datos.title || `Error HTTP ${respuesta.status}`);
  }
  return datos;
}

/** Sends a command to the WRITE side (with the token). Returns {id, mensaje}. */
async function enviarComando(metodo, ruta, cuerpo) {
  let respuesta;
  try {
    respuesta = await fetch(API.comandos + ruta, {
      method: metodo,
      headers: { 'Content-Type': 'application/json', ...cabeceraAutorizacion() },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
  } catch {
    throw new Error('No hay conexión con el servicio de COMANDOS (puerto 8081). ¿Está encendido?');
  }
  return leerRespuesta(respuesta);
}

/** Reads data from the READ side (with the token if there is a session). */
async function consultar(ruta, opciones = {}) {
  let respuesta;
  try {
    respuesta = await fetch(API.consultas + ruta, {
      ...opciones,
      headers: { ...(opciones.headers || {}), ...cabeceraAutorizacion() },
    });
  } catch {
    throw new Error('No hay conexión con el servicio de CONSULTAS (puerto 8082). ¿Está encendido?');
  }
  return leerRespuesta(respuesta);
}

// ---------------------------------------------------------------- live events (Server-Sent Events)

/**
 * Opens the live channel. `manejadores` = { calzado(evento), pedido(evento), reconstruccion(), conectado() }.
 * EventSource cannot send headers, so the token goes in the URL (only for this endpoint).
 * The server decides what each user receives: visitors get catalog changes, a customer also gets
 * THEIR orders, and the admin gets everything.
 */
function conectarTiempoReal(manejadores) {
  const indicador = $('#conexion');
  const marcar = (enLinea) => {
    if (!indicador) return;
    indicador.textContent = enLinea ? '● En vivo' : '● Reconectando…';
    indicador.className = 'conexion ' + (enLinea ? 'en-linea' : 'fuera');
  };

  const sesion = obtenerSesion();
  const url = API.consultas + '/stream' + (sesion ? '?access_token=' + encodeURIComponent(sesion.token) : '');
  const fuente = new EventSource(url);
  fuente.addEventListener('conectado', () => {
    marcar(true);
    manejadores.conectado?.();
  });
  fuente.onerror = () => marcar(false);

  for (const nombre of ['calzado', 'pedido', 'reconstruccion']) {
    fuente.addEventListener(nombre, (mensaje) => {
      const evento = JSON.parse(mensaje.data);
      registrarActividad(nombre, evento);
      manejadores[nombre]?.(evento);
    });
  }
  return fuente;
}

/** Writes the event in the "Actividad en vivo" panel. */
function registrarActividad(nombre, evento) {
  const lista = $('#actividad');
  if (!lista) return;
  $('#actividad-vacia')?.remove();
  const item = document.createElement('li');
  item.innerHTML = `
    <time>${new Date().toLocaleTimeString('es-CO')}</time>
    <span class="etiqueta ${nombre}">${escapar(evento.tipo || 'RECONSTRUCCION')}</span>
    <span>${escapar(describirEvento(nombre, evento))}</span>`;
  lista.prepend(item);
  while (lista.children.length > 40) lista.lastElementChild.remove();
}

/** One-line, human readable summary of an event for the activity panel. */
function describirEvento(nombre, e) {
  switch (nombre) {
    case 'calzado':
      return `#${e.calzadoId} ${e.marca} ${e.modelo} · talla ${talla(e.talla)} · stock ${e.stock} · ${pesos(e.precio)}`;
    case 'pedido':
      return `Pedido #${e.pedidoId} · ${e.clienteNombre} · ${e.cantidad} × ${e.descripcionCalzado} · ${e.estado}`;
    default:
      return 'Modelo de lectura borrado: reproduciendo los eventos de Kafka desde el inicio…';
  }
}

// ---------------------------------------------------------------- helpers

/**
 * Escapes text before putting it inside innerHTML. Without this, a shoe called
 * "<img src=x onerror=alert(1)>" would execute code in every browser (XSS attack) and could steal the token.
 */
function escapar(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

const formatoPesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
/** Formats a price as Colombian pesos, e.g. "$ 459.900". */
const pesos = (valor) => formatoPesos.format(Number(valor));
/** Shows a size with a decimal comma, e.g. 40.5 -> "40,5". */
const talla = (t) => String(t).replace('.', ',');
/** Formats an ISO-8601 date as a short local date and time. */
const fecha = (iso) => new Date(iso).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });

/** Groups many calls in a short time into one (e.g. 50 replayed events -> 1 reload). */
function agrupar(funcion, ms = 250) {
  let temporizador;
  return (...args) => {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => funcion(...args), ms);
  };
}

/** Small notification in the bottom-right corner. tipo: info | exito | error */
function avisar(mensaje, tipo = 'info') {
  const contenedor = $('#avisos');
  if (!contenedor) return;
  const aviso = document.createElement('div');
  aviso.className = `aviso ${tipo}`;
  aviso.textContent = mensaje;
  contenedor.append(aviso);
  setTimeout(() => aviso.remove(), tipo === 'error' ? 9000 : 7000);
}

/** Coloured label for an order state (CREADO, DESPACHADO, CANCELADO). */
function etiquetaEstado(estado) {
  return `<span class="estado ${escapar(estado).toLowerCase()}">${escapar(estado)}</span>`;
}
