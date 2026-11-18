package com.tienda.comandos.mensajeria;

import com.tienda.comandos.dominio.Calzado;
import com.tienda.comandos.dominio.Pedido;
import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.TipoEventoCalzado;
import com.tienda.eventos.TipoEventoPedido;

import java.time.Instant;

/** Translates write-model entities into contract events (a snapshot of the current state). */
public final class EventoMapper {

    private EventoMapper() {
    }

    /** Snapshot of the shoe after the change, labelled with what happened. */
    public static CalzadoEvento deCalzado(TipoEventoCalzado tipo, Calzado c) {
        return new CalzadoEvento(tipo, c.getId(), c.getMarca(), c.getModelo(), c.getCategoria(),
                c.getTalla(), c.getColor(), c.getPrecio(), c.getStock(), Instant.now());
    }

    /** Snapshot of the order, including a copy of the shoe description. */
    public static PedidoEvento dePedido(TipoEventoPedido tipo, Pedido p) {
        Calzado c = p.getCalzado();
        return new PedidoEvento(tipo, p.getId(), c.getId(), c.descripcion(), p.getCantidad(),
                p.getPrecioUnitario(), p.getTotal(), p.getClienteNombre(), p.getClienteEmail(),
                p.getEstado(), p.getCreadoEn(), Instant.now());
    }
}
