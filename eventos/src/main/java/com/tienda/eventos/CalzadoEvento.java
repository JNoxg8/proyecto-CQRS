package com.tienda.eventos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published on {@link Topicos#CALZADOS} every time a shoe changes.
 *
 * <p>It carries the <b>complete current state</b> of the shoe (not only what changed).
 * This style is called <i>event-carried state transfer</i>: consumers never need to call
 * the write side back, and processing the same event twice gives the same result (idempotent).
 */
public record CalzadoEvento(
        TipoEventoCalzado tipo,
        Long calzadoId,
        String marca,
        String modelo,
        String categoria,
        double talla,
        String color,
        BigDecimal precio,
        int stock,
        Instant ocurridoEn) {
}
