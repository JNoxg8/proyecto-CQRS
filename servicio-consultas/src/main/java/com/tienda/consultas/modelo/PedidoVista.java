package com.tienda.consultas.modelo;

import com.tienda.eventos.EstadoPedido;
import com.tienda.eventos.PedidoEvento;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model of an order. It is <b>denormalized</b>: it already contains the shoe description and the
 * price paid, so "my orders" is a single query without joins (in PostgreSQL it needs pedidos JOIN calzados).
 */
@Document(collection = "pedidos")
public record PedidoVista(
        @Id Long id,
        Long calzadoId,
        String descripcionCalzado,
        int cantidad,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precioUnitario,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal total,
        String clienteNombre,
        @Indexed String clienteEmail,         // the Tienda client searches orders by e-mail
        EstadoPedido estado,
        Instant creadoEn,
        Instant actualizadoEn) {

    /** Builds the denormalized document from the event. */
    public static PedidoVista desde(PedidoEvento evento) {
        return new PedidoVista(evento.pedidoId(), evento.calzadoId(), evento.descripcionCalzado(),
                evento.cantidad(), evento.precioUnitario(), evento.total(), evento.clienteNombre(),
                evento.clienteEmail(), evento.estado(), evento.creadoEn(), evento.ocurridoEn());
    }
}
