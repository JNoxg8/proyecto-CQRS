package com.tienda.comandos.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.Topicos;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 1 of the Transactional Outbox: stores each domain event in the outbox table.
 *
 * <p>The services call {@code ApplicationEventPublisher.publishEvent(...)} inside their transaction.
 * {@code @EventListener} runs right there, synchronously, so the INSERT into outbox_eventos belongs to the
 * same transaction as the order or the stock change:
 * <ul>
 *   <li>the transaction commits → the change and its event are both stored;</li>
 *   <li>the transaction rolls back (e.g. not enough stock) → neither is stored.</li>
 * </ul>
 * Kafka is not contacted here at all: buying works even when Kafka is down. {@link RelevoOutbox} sends later.
 *
 * <p>{@code Propagation.MANDATORY}: fails loudly if someone publishes an event outside a transaction,
 * because then the "all or nothing" guarantee would not hold.
 */
@Component
public class RegistradorOutbox {

    private final EventoOutboxRepository outbox;
    private final ObjectMapper json;

    public RegistradorOutbox(EventoOutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /** Shoe events -> topic calzado-eventos, key = calzadoId. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(CalzadoEvento evento) {
        guardar(Topicos.CALZADOS, evento.calzadoId(), evento.tipo().name(), evento);
    }

    /** Order events -> topic pedido-eventos, key = pedidoId. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(PedidoEvento evento) {
        guardar(Topicos.PEDIDOS, evento.pedidoId(), evento.tipo().name(), evento);
    }

    private void guardar(String topico, Long clave, String tipo, Object evento) {
        try {
            outbox.save(new EventoOutbox(topico, String.valueOf(clave), tipo, json.writeValueAsString(evento)));
        } catch (JsonProcessingException e) {
            // Rolls back the whole business transaction: better to fail the command than to lose its event
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
