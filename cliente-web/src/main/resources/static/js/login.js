/*
 * LOGIN / SIGN-UP page. Talks to servicio-autenticacion, stores the session (token) and sends the user
 * to the right app: ADMIN -> admin.html, CLIENTE -> tienda.html (or back to the page that sent them here).
 */

const parametros = new URLSearchParams(location.search);
const MOTIVOS = {
  admin: 'Esa sección es solo para administradores. Inicia sesión con una cuenta de administrador.',
  expirada: 'Tu sesión expiró o ya no es válida. Inicia sesión de nuevo.',
};
if (MOTIVOS[parametros.get('motivo')]) {
  $('#motivo').textContent = MOTIVOS[parametros.get('motivo')];
  $('#motivo').hidden = false;
}

/**
 * Where to go after logging in. Only pages of THIS app are accepted (e.g. "tienda.html"):
 * accepting any URL in ?volver= would allow an "open redirect" to a phishing site.
 */
function destino(sesion) {
  const volver = parametros.get('volver');
  const valido = volver && /^[a-z-]+\.html$/.test(volver) && volver !== 'login.html';
  if (valido && !(volver === 'admin.html' && !esAdmin(sesion))) {
    return volver;
  }
  return esAdmin(sesion) ? 'admin.html' : 'tienda.html';
}

/**
 * POST to servicio-autenticacion.
 * @returns {Promise<{token, tipo, expiraEn, nombre, email, rol}>}
 */
async function autenticar(ruta, cuerpo) {
  let respuesta;
  try {
    respuesta = await fetch(API.autenticacion + ruta, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo),
    });
  } catch {
    throw new Error('No hay conexión con el servicio de AUTENTICACIÓN (puerto 8083). ¿Está encendido?');
  }
  const datos = await respuesta.json().catch(() => ({}));
  if (!respuesta.ok) {
    throw new Error(datos.detail || datos.title || `Error HTTP ${respuesta.status}`);
  }
  return datos;   // {token, tipo, expiraEn, nombre, email, rol}
}

/** On submit: authenticate, store the session and go to the right page. */
function conectarFormulario(selector, ruta) {
  const formulario = $(selector);
  formulario.addEventListener('submit', async (e) => {
    e.preventDefault();
    const boton = formulario.querySelector('button');
    boton.disabled = true;
    try {
      const sesion = await autenticar(ruta, Object.fromEntries(new FormData(formulario)));
      guardarSesion(sesion);
      location.href = destino(sesion);
    } catch (error) {
      avisar(error.message, 'error');
      boton.disabled = false;
    }
  });
}

conectarFormulario('#form-login', '/login');
conectarFormulario('#form-registro', '/registro');
