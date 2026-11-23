package com.tienda.comandos.mensajeria;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One row of the <b>Transactional Outbox</b>: an event waiting to be sent to Kafka.
 *
 * <p>It is saved in the SAME database transaction as the business change (the order, the stock...).
 * Either both are stored or neither is, so an event can never be lost and Kafka can never announce
 * something that was rolled back. {@link RelevoOutbox} sends the pending rows later.
 */
@Entity
@Table(name = "outbox_eventos", indexes = @Index(name = "idx_outbox_pendientes", columnList = "enviadoEn"))
public class EventoOutbox {

    /** Increasing id = the order in which the events happened (the relay sends them in this order). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topico;

    /** Kafka message key (the id of the shoe or order): keeps the order of the events of the same entity. */
    @Column(nullable = false)
    private String clave;

    /** Event type (e.g. PEDIDO_CREADO), only to make the table and the logs readable. */
    @Column(nullable = false)
    private String tipo;

    /** The event as JSON. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant creadoEn;

    /** null = still pending. */
    private Instant enviadoEn;

    protected EventoOutbox() {
        // required by JPA
    }

    public EventoOutbox(String topico, String clave, String tipo, String payload) {
        this.topico = topico;
        this.clave = clave;
        this.tipo = tipo;
        this.payload = payload;
        this.creadoEn = Instant.now();
    }

    /** Kafka confirmed the message: it will not be sent again. */
    public void marcarEnviado() {
        this.enviadoEn = Instant.now();
    }

    public boolean estaPendiente() {
        return enviadoEn == null;
    }

    public Long getId() {
        return id;
    }

    public String getTopico() {
        return topico;
    }

    public String getClave() {
        return clave;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }
}
