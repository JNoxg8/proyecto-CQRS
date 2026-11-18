/*
 * TIENDA client app (the buyer).
 *   - Anyone can browse the catalog (public query).
 *   - Buying and "my orders" require logging in as a customer (or admin).
 *   - Who is buying comes from the token: the page never sends a name or e-mail.
 */

const sesion = obtenerSesion();
pintarSesion();
$('#enlace-admin').hidden = !esAdmin(sesion);

// ---------------------------------------------------------------- catalog (public query)

let ultimoCalzadoCambiado = null;   // to flash the card that changed

async function cargarCatalogo() {
  const filtros = new URLSearchParams();
  if ($('#f-marca').value) filtros.set('marca', $('#f-marca').value);
  if ($('#f-categoria').value) filtros.set('categoria', $('#f-categoria').value);
  if ($('#f-talla').value) filtros.set('talla', $('#f-talla').value);
  if ($('#f-precio').value) filtros.set('precioMax', $('#f-precio').value);
  if ($('#f-disponibles').checked) filtros.set('soloDisponibles', 'true');

  try {
    const [todos, filtrados] = await Promise.all([consultar('/catalogo'), consultar('/catalogo?' + filtros)]);
    llenarOpciones(todos);
    pintarCatalogo(filtrados);
  } catch (error) {
    $('#catalogo').innerHTML = `<p class="vacio">${escapar(error.message)}</p>`;
  }
}

/** Fills the filter selects with the values that exist in the catalog, keeping the current selection. */
function llenarOpciones(calzados) {
  const opciones = {
    '#f-marca': calzados.map((c) => c.marca),
    '#f-categoria': calzados.map((c) => c.categoria),
    '#f-talla': calzados.map((c) => c.talla),
  };
  for (const [selector, valores] of Object.entries(opciones)) {
    const select = $(selector);
    const elegido = select.value;
    const unicos = [...new Set(valores)].sort((a, b) => (a > b ? 1 : -1));
    select.innerHTML = '<option value="">Todas</option>' + unicos
      .map((v) => `<option value="${escapar(v)}">${escapar(selector === '#f-talla' ? talla(v) : v)}</option>`)
      .join('');
    select.value = unicos.map(String).includes(elegido) ? elegido : '';
  }
}

function pintarCatalogo(calzados) {
  if (calzados.length === 0) {
    $('#catalogo').innerHTML = '<p class="vacio">No hay calzado con esos filtros.</p>';
    return;
  }
  const textoBoton = sesion ? 'Comprar' : 'Inicia sesión para comprar';
  $('#catalogo').innerHTML = calzados.map((c) => {
    const agotado = c.stock === 0;
    const claseStock = agotado ? 'cero' : c.stock <= 2 ? 'bajo' : 'ok';
    const resaltar = c.id === ultimoCalzadoCambiado ? ' resaltado' : '';
    return `
      <article class="producto${agotado ? ' agotado' : ''}${resaltar}">
        <span class="categoria">${escapar(c.categoria)}</span>
        <h3>${escapar(c.marca)} ${escapar(c.modelo)}</h3>
        <p class="detalle">Talla ${talla(c.talla)} · ${escapar(c.color)} · ref #${c.id}</p>
        <p class="precio">${pesos(c.precio)}</p>
        <span class="stock ${claseStock}">${agotado ? 'Agotado' : c.stock + ' disponible(s)'}</span>
        <div class="comprar">
          ${sesion ? `<input type="number" min="1" max="10" value="1" aria-label="Cantidad" ${agotado ? 'disabled' : ''}>` : ''}
          <button data-id="${c.id}" ${agotado ? 'disabled' : ''}>${textoBoton}</button>
        </div>
      </article>`;
  }).join('');
  ultimoCalzadoCambiado = null;
}

$('#filtros').addEventListener('change', cargarCatalogo);
$('#filtros').addEventListener('reset', () => setTimeout(cargarCatalogo));
$('#f-precio').addEventListener('input', agrupar(cargarCatalogo, 400));

// ---------------------------------------------------------------- buying (command, requires login)

const enEspera = new Map();   // pedidoId -> moment the command was accepted
const yaVistos = new Map();   // pedidoId -> moment its event arrived (it can arrive before the HTTP response!)

$('#catalogo').addEventListener('click', async (e) => {
  const boton = e.target.closest('button[data-id]');
  if (!boton) return;
  if (!sesion) {
    irALogin();
    return;
  }
  const cantidad = Number(boton.parentElement.querySelector('input').value);
  boton.disabled = true;
  const inicio = performance.now();
  try {
    // Only WHAT to buy. WHO buys is taken by the server from the token.
    const respuesta = await enviarComando('POST', '/pedidos', { calzadoId: Number(boton.dataset.id), cantidad });
    if (yaVistos.has(respuesta.id)) {
      informarConsistencia(respuesta.id, yaVistos.get(respuesta.id) - inicio);
    } else {
      enEspera.set(respuesta.id, inicio);
      avisar(`Pedido #${respuesta.id} aceptado por el servicio de COMANDOS (guardado en PostgreSQL). `
        + 'Esperando que llegue al servicio de CONSULTAS…', 'info');
    }
  } catch (error) {
    avisar(error.message, 'error');   // e.g. "Stock insuficiente..." (409)
  } finally {
    boton.disabled = false;
  }
});

function informarConsistencia(pedidoId, milisegundos) {
  avisar(`✓ Pedido #${pedidoId} ya está en MongoDB (servicio de consultas), ${Math.max(0, Math.round(milisegundos))} ms `
    + 'después de guardarse en PostgreSQL. Eso es la consistencia eventual.', 'exito');
}

// ---------------------------------------------------------------- my orders (query, requires login)

async function cargarMisPedidos() {
  if (!sesion) {
    $('#mis-pedidos').innerHTML = '<p class="vacio"><a href="#" id="login-pedidos">Inicia sesión</a> para comprar y ver tus pedidos.</p>';
    $('#login-pedidos').addEventListener('click', (e) => { e.preventDefault(); irALogin(); });
    return;
  }
  try {
    pintarMisPedidos(await consultar('/pedidos/mios'));
  } catch (error) {
    $('#mis-pedidos').innerHTML = `<p class="vacio">${escapar(error.message)}</p>`;
  }
}

function pintarMisPedidos(pedidos) {
  if (pedidos.length === 0) {
    $('#mis-pedidos').innerHTML = '<p class="vacio">Todavía no tienes pedidos.</p>';
    return;
  }
  $('#mis-pedidos').innerHTML = `
    <table>
      <thead><tr><th>#</th><th>Fecha</th><th>Calzado</th><th>Cant.</th><th>Total</th><th>Estado</th><th></th></tr></thead>
      <tbody>${pedidos.map((p) => `
        <tr>
          <td>${p.id}</td>
          <td>${fecha(p.creadoEn)}</td>
          <td>${escapar(p.descripcionCalzado)}</td>
          <td>${p.cantidad}</td>
          <td>${pesos(p.total)}</td>
          <td>${etiquetaEstado(p.estado)}</td>
          <td>${p.estado === 'CREADO'
            ? `<button class="secundario pequeno" data-cancelar="${p.id}">Cancelar</button>` : ''}</td>
        </tr>`).join('')}
      </tbody>
    </table>`;
}

$('#mis-pedidos').addEventListener('click', async (e) => {
  const boton = e.target.closest('button[data-cancelar]');
  if (!boton) return;
  boton.disabled = true;
  try {
    const respuesta = await enviarComando('POST', `/pedidos/${boton.dataset.cancelar}/cancelar`);
    avisar(`${respuesta.mensaje}. Las unidades vuelven al inventario.`, 'info');
  } catch (error) {
    avisar(error.message, 'error');
    boton.disabled = false;
  }
});

// ---------------------------------------------------------------- live updates

const recargarCatalogo = agrupar(cargarCatalogo);
const recargarPedidos = agrupar(cargarMisPedidos);

conectarTiempoReal({
  conectado() {
    recargarCatalogo();
    recargarPedidos();
  },
  calzado(evento) {
    ultimoCalzadoCambiado = evento.calzadoId;
    recargarCatalogo();
  },
  pedido(evento) {
    // The server only sends us OUR orders (or every order if we are admin)
    if (evento.tipo === 'PEDIDO_CREADO') {
      if (enEspera.has(evento.pedidoId)) {
        informarConsistencia(evento.pedidoId, performance.now() - enEspera.get(evento.pedidoId));
        enEspera.delete(evento.pedidoId);
      } else {
        yaVistos.set(evento.pedidoId, performance.now());
      }
    }
    if (sesion && evento.clienteEmail === sesion.email) {
      recargarPedidos();
    }
  },
  reconstruccion() {
    recargarCatalogo();
    recargarPedidos();
  },
});

cargarCatalogo();
cargarMisPedidos();
