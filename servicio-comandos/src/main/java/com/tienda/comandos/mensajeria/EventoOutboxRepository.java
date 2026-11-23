package com.tienda.comandos.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Access to the outbox table (outbox_eventos). */
public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, Long> {

    /** The oldest pending events first (at most 100 per round). */
    List<EventoOutbox> findTop100ByEnviadoEnIsNullOrderByIdAsc();

    /** How many events are still waiting for Kafka. */
    long countByEnviadoEnIsNull();

    /** Housekeeping: deletes the events that were sent before the given instant. */
    @Transactional
    long deleteByEnviadoEnBefore(Instant limite);
}
