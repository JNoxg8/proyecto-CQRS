/*
 * ADMIN client app (the store manager).
 *   - SENDS commands: register shoe, change price, restock, ship order, cancel order.
 *   - READS inventory and orders from servicio-consultas and refreshes them live.
 *
 * Access guard: without an ADMIN session the page sends you to the login.
 * This check in the browser is only for usability: anyone could edit this JavaScript.
 * The REAL protection is in the APIs, which answer 401/403 to any token that is not ADMIN.
 */
const sesion = obtenerSesion();
if (!esAdmin(sesion)) {
  location.replace('login.html?volver=admin.html&motivo=admin');
} else {
  document.body.hidden = false;
  pintarSesion();
}

let inventario = [];
let pedidos = [];
let ultimoCambio = { calzado: null, pedido: null };   // to flash the row that changed

// ---------------------------------------------------------------- queries

async function cargarInventario() {
  try {
    inventario = await consultar('/catalogo');
    pintarInventario();
  } catch (error) {
    $('#inventario').innerHTML = `<tr><td colspan="6" class="vacio">${escapar(error.message)}</td></tr>`;
  }
  pintarResumen();
}

async function cargarPedidos() {
  try {
    pedidos = await consultar('/pedidos');
    pintarPedidos();
  } catch (error) {
    $('#pedidos').innerHTML = `<tr><td colspan="8" class="vacio">${escapar(error.message)}</td></tr>`;
  }
  pintarResumen();
}

function pintarResumen() {
  const validos = pedidos.filter((p) => p.estado !== 'CANCELADO');
  $('#r-pedidos').textContent = pedidos.length;
  $('#r-ventas').textContent = pesos(validos.reduce((suma, p) => suma + Number(p.total), 0));
  $('#r-unidades').textContent = inventario.reduce((suma, c) => suma + c.stock, 0);
  $('#r-agotados').textContent = inventario.filter((c) => c.stock === 0).length;
}

function pintarInventario() {
  if (inventario.length === 0) {
    $('#inventario').innerHTML = '<tr><td colspan="6" class="vacio">No hay calzado registrado.</td></tr>';
    return;
  }
  $('#inventario').innerHTML = inventario.map((c) => {
    const claseStock = c.stock === 0 ? 'cero' : c.stock <= 2 ? 'bajo' : 'ok';
    return `
      <tr data-id="${c.id}" class="${c.id === ultimoCambio.calzado ? 'resaltado' : ''}">
        <td>${c.id}</td>
        <td><strong>${escapar(c.marca)} ${escapar(c.modelo)}</strong><br><small>${escapar(c.categoria)}</small></td>
        <td>${talla(c.talla)}</td>
        <td>${escapar(c.color)}</td>
        <td>${pesos(c.precio)}
          <div class="mini">
            <input type="number" min="1" step="100" placeholder="Nuevo precio" aria-label="Nuevo precio">
            <button class="secundario pequeno" data-accion="precio">Cambiar</button>
          </div>
        </td>
        <td><span class="stock ${claseStock}">${c.stock === 0 ? 'Agotado' : c.stock}</span>
          <div class="mini">
            <input type="number" min="1" max="1000" value="5" aria-label="Unidades a reponer">
            <button class="secundario pequeno" data-accion="reponer">Reponer</button>
          </div>
        </td>
      </tr>`;
  }).join('');
  ultimoCambio.calzado = null;
}

function pintarPedidos() {
  if (pedidos.length === 0) {
    $('#pedidos').innerHTML = '<tr><td colspan="8" class="vacio">Todavía no hay pedidos.</td></tr>';
    return;
  }
  $('#pedidos').innerHTML = pedidos.map((p) => `
    <tr data-id="${p.id}" class="${p.id === ultimoCambio.pedido ? 'resaltado' : ''}">
      <td>${p.id}</td>
      <td>${fecha(p.creadoEn)}</td>
      <td>${escapar(p.clienteNombre)}<br><small>${escapar(p.clienteEmail)}</small></td>
      <td>${escapar(p.descripcionCalzado)}</td>
      <td>${p.cantidad}</td>
      <td>${pesos(p.total)}</td>
      <td>${etiquetaEstado(p.estado)}</td>
      <td>${p.estado === 'CREADO' ? `
        <div class="acciones">
          <button class="exito pequeno" data-accion="despachar">Despachar</button>
          <button class="secundario pequeno" data-accion="cancelar">Cancelar</button>
        </div>` : ''}</td>
    </tr>`).join('');
  ultimoCambio.pedido = null;
}

// ---------------------------------------------------------------- commands

/** Runs a command, shows the result and re-enables the button. The table refreshes when the event arrives. */
async function ejecutar(boton, metodo, ruta, cuerpo) {
  boton.disabled = true;
  try {
    const respuesta = await enviarComando(metodo, ruta, cuerpo);
    avisar(`${respuesta.mensaje}. Esperando el evento…`, 'info');
  } catch (error) {
    avisar(error.message, 'error');
  } finally {
    boton.disabled = false;
  }
}

$('#form-registrar').addEventListener('submit', async (e) => {
  e.preventDefault();
  const formulario = e.target;
  const datos = Object.fromEntries(new FormData(formulario));
  const boton = formulario.querySelector('button');
  boton.disabled = true;
  try {
    const respuesta = await enviarComando('POST', '/calzados', {
      ...datos,
      talla: Number(datos.talla),
      precio: Number(datos.precio),
      stockInicial: Number(datos.stockInicial),
    });
    avisar(`${respuesta.mensaje}. Aparecerá en el inventario cuando llegue el evento.`, 'exito');
    formulario.reset();
  } catch (error) {
    avisar(error.message, 'error');
  } finally {
    boton.disabled = false;
  }
});

$('#inventario').addEventListener('click', (e) => {
  const boton = e.target.closest('button[data-accion]');
  if (!boton) return;
  const id = boton.closest('tr').dataset.id;
  const valor = Number(boton.parentElement.querySelector('input').value);
  if (!valor || valor <= 0) {
    avisar('Escribe un valor mayor que cero.', 'error');
    return;
  }
  if (boton.dataset.accion === 'precio') {
    ejecutar(boton, 'PUT', `/calzados/${id}/precio`, { nuevoPrecio: valor });
  } else {
    ejecutar(boton, 'POST', `/calzados/${id}/reponer-stock`, { cantidad: valor });
  }
});

$('#pedidos').addEventListener('click', (e) => {
  const boton = e.target.closest('button[data-accion]');
  if (!boton) return;
  const id = boton.closest('tr').dataset.id;
  ejecutar(boton, 'POST', `/pedidos/${id}/${boton.dataset.accion}`);
});

$('#reconstruir').addEventListener('click', async (e) => {
  const boton = e.target;
  boton.disabled = true;
  try {
    const respuesta = await consultar('/admin/reconstruir', { method: 'POST' });
    avisar(respuesta.mensaje, 'info');
  } catch (error) {
    avisar(error.message, 'error');
  } finally {
    setTimeout(() => { boton.disabled = false; }, 3000);
  }
});

// ---------------------------------------------------------------- live updates

const recargarInventario = agrupar(cargarInventario);
const recargarPedidos = agrupar(cargarPedidos);

if (esAdmin(sesion)) {
  conectarTiempoReal({
    conectado() {
      recargarInventario();
      recargarPedidos();
    },
    calzado(evento) {
      ultimoCambio.calzado = evento.calzadoId;
      recargarInventario();
    },
    pedido(evento) {
      ultimoCambio.pedido = evento.pedidoId;
      recargarPedidos();
    },
    reconstruccion() {
      recargarInventario();
      recargarPedidos();
    },
  });

  cargarInventario();
  cargarPedidos();
}
