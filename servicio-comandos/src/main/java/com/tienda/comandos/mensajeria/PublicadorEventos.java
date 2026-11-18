package com.tienda.comandos.mensajeria;

import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.Topicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the domain events to Kafka <b>only after the database transaction commits</b>.
 *
 * <p>The services call {@code ApplicationEventPublisher.publishEvent(...)} inside the transaction;
 * Spring holds the event and delivers it here after COMMIT. If the transaction fails (e.g. not enough
 * stock), the event is discarded, so Kafka never announces something that did not happen.
 *
 * <p>Known limit: if Kafka is down right after the commit, the event is lost. Production systems solve
 * it with the Transactional Outbox pattern (store the event in the same DB transaction, send it later).
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final KafkaTemplate<String, Object> kafka;

    public PublicadorEventos(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CalzadoEvento evento) {
        enviar(Topicos.CALZADOS, evento.calzadoId(), evento.tipo().name(), evento);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(PedidoEvento evento) {
        enviar(Topicos.PEDIDOS, evento.pedidoId(), evento.tipo().name(), evento);
    }

    /**
     * The key decides the partition: every event of the same shoe (or order) goes to the same partition,
     * so consumers receive them in the order they happened.
     */
    private void enviar(String topico, Long clave, String tipo, Object evento) {
        kafka.send(topico, String.valueOf(clave), evento).whenComplete((resultado, error) -> {
            if (error != null) {
                log.error("No se pudo publicar {} (clave {}) en {}: {}", tipo, clave, topico, error.getMessage());
            } else {
                log.info("--> Publicado {} en '{}' [particion {}, offset {}] clave={}", tipo, topico,
                        resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), clave);
            }
        });
    }
}
