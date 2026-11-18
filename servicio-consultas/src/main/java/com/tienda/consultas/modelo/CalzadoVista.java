package com.tienda.consultas.modelo;

import com.tienda.eventos.CalzadoEvento;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model of a shoe: one document of the "catalogo" collection, shaped exactly as the clients show it.
 * It is an immutable record: the projection always replaces the whole document with the latest state.
 */
@Document(collection = "catalogo")
public record CalzadoVista(
        @Id Long id,                          // same id as in PostgreSQL
        @Indexed String marca,
        String modelo,
        @Indexed String categoria,
        double talla,
        String color,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precio,   // numeric in Mongo (default would be a String)
        int stock,
        boolean disponible,                   // pre-computed for the clients: stock > 0
        Instant actualizadoEn) {

    /** Builds the document from the event (the event carries the full state). */
    public static CalzadoVista desde(CalzadoEvento evento) {
        return new CalzadoVista(evento.calzadoId(), evento.marca(), evento.modelo(), evento.categoria(),
                evento.talla(), evento.color(), evento.precio(), evento.stock(), evento.stock() > 0,
                evento.ocurridoEn());
    }
}
