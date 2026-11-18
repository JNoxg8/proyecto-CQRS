package com.tienda.comandos.aplicacion;

import com.tienda.comandos.comandos.CrearPedidoComando;
import com.tienda.comandos.dominio.AccesoDenegadoException;
import com.tienda.comandos.dominio.Calzado;
import com.tienda.comandos.dominio.Pedido;
import com.tienda.comandos.dominio.RecursoNoEncontradoException;
import com.tienda.comandos.mensajeria.EventoMapper;
import com.tienda.comandos.repositorio.CalzadoRepository;
import com.tienda.comandos.repositorio.PedidoRepository;
import com.tienda.eventos.TipoEventoCalzado;
import com.tienda.eventos.TipoEventoPedido;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the order commands. Creating or cancelling an order changes TWO aggregates (order + stock),
 * so it announces TWO events, one per topic.
 */
@Service
public class PedidoServicio {

    private final PedidoRepository pedidos;
    private final CalzadoRepository calzados;
    private final ApplicationEventPublisher eventos;

    public PedidoServicio(PedidoRepository pedidos, CalzadoRepository calzados, ApplicationEventPublisher eventos) {
        this.pedidos = pedidos;
        this.calzados = calzados;
        this.eventos = eventos;
    }

    /** The buyer is the authenticated user (name and e-mail come from the token). */
    @Transactional
    public Long crear(CrearPedidoComando comando, UsuarioActual comprador) {
        Calzado calzado = calzados.findById(comando.calzadoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el calzado con id " + comando.calzadoId()));

        // Business rule inside the domain: throws if there is not enough stock -> nothing is saved or published.
        Pedido pedido = pedidos.save(Pedido.crear(calzado, comando.cantidad(), comprador.nombre(), comprador.email()));

        eventos.publishEvent(EventoMapper.deCalzado(TipoEventoCalzado.STOCK_DESCONTADO, calzado));
        eventos.publishEvent(EventoMapper.dePedido(TipoEventoPedido.PEDIDO_CREADO, pedido));
        return pedido.getId();
    }

    @Transactional
    public void despachar(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        pedido.despachar();
        eventos.publishEvent(EventoMapper.dePedido(TipoEventoPedido.PEDIDO_DESPACHADO, pedido));
    }

    /** A customer can only cancel THEIR OWN orders; an admin can cancel any. */
    @Transactional
    public void cancelar(Long pedidoId, UsuarioActual usuario) {
        Pedido pedido = buscar(pedidoId);
        if (!usuario.esAdmin() && !pedido.perteneceA(usuario.email())) {
            throw new AccesoDenegadoException("Solo puedes cancelar tus propios pedidos");
        }
        pedido.cancelar();   // also gives the units back to the stock
        eventos.publishEvent(EventoMapper.deCalzado(TipoEventoCalzado.STOCK_DEVUELTO, pedido.getCalzado()));
        eventos.publishEvent(EventoMapper.dePedido(TipoEventoPedido.PEDIDO_CANCELADO, pedido));
    }

    private Pedido buscar(Long id) {
        return pedidos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido #" + id));
    }
}
