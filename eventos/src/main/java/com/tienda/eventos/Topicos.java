package com.tienda.eventos;

/**
 * Kafka topic names. Kept in the contract so producer and consumers can never disagree on a name.
 */
public final class Topicos {

    /** Everything that happens to a shoe: registered, price changed, stock changed. Key = calzadoId. */
    public static final String CALZADOS = "calzado-eventos";

    /** Everything that happens to an order: created, shipped, cancelled. Key = pedidoId. */
    public static final String PEDIDOS = "pedido-eventos";

    /**
     * Dead Letter Topics: a consumer sends here the messages it could not process after all its retries
     * (or that are corrupt), instead of losing them. Name = original topic + ".DLT" (Spring Kafka convention).
     */
    public static final String CALZADOS_DLT = CALZADOS + ".DLT";

    /** Dead Letter Topic of {@link #PEDIDOS}. */
    public static final String PEDIDOS_DLT = PEDIDOS + ".DLT";

    private Topicos() {
    }
}
