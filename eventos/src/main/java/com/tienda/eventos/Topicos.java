package com.tienda.eventos;

/**
 * Kafka topic names. Kept in the contract so producer and consumers can never disagree on a name.
 */
public final class Topicos {

    /** Everything that happens to a shoe: registered, price changed, stock changed. Key = calzadoId. */
    public static final String CALZADOS = "calzado-eventos";

    /** Everything that happens to an order: created, shipped, cancelled. Key = pedidoId. */
    public static final String PEDIDOS = "pedido-eventos";

    private Topicos() {
    }
}
