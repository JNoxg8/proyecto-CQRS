package com.tienda.eventos;

/**
 * Life cycle of an order:  CREADO -> DESPACHADO
 *                                 -> CANCELADO
 */
public enum EstadoPedido {
    CREADO,
    DESPACHADO,
    CANCELADO
}
