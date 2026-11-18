package com.tienda.eventos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published on {@link Topicos#PEDIDOS} every time an order changes.
 *
 * <p>Note {@code descripcionCalzado} and {@code precioUnitario}: the order carries a copy of the shoe
 * data <i>as it was when the order was placed</i>. The read side stores it like that (denormalized),
 * so showing an order never needs a join, and a later price change does not alter past orders.
 */
public record PedidoEvento(
        TipoEventoPedido tipo,
        Long pedidoId,
        Long calzadoId,
        String descripcionCalzado,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal total,
        String clienteNombre,
        String clienteEmail,
        EstadoPedido estado,
        Instant creadoEn,
        Instant ocurridoEn) {
}
