package com.tienda.comandos.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.Topicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Step 2 of the Transactional Outbox: the <b>relay</b>. Every second it reads the pending events
 * (oldest first), sends each one to Kafka, WAITS for Kafka's confirmation and only then marks it as sent.
 *
 * <ul>
 *   <li><b>Kafka down</b>: the send fails, the event stays pending and the relay stops this round
 *       (it does not skip it, so the order is kept). Next round it tries again. Nothing is lost.</li>
 *   <li><b>At-least-once</b>: if the service dies after Kafka confirmed but before the row is marked,
 *       the event is sent twice. That is fine because the consumers are idempotent (upsert by id).</li>
 *   <li>Limit: with several instances of this service, each one would run the relay; that would need
 *       a lock ({@code SELECT ... FOR UPDATE SKIP LOCKED}) or a CDC tool such as Debezium.</li>
 * </ul>
 */
@Component
public class RelevoOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelevoOutbox.class);

    /** Allow-list topic -> event class (the JSON is turned back into the record before sending it). */
    private static final Map<String, Class<?>> CLASES = Map.of(
            Topicos.CALZADOS, CalzadoEvento.class,
            Topicos.PEDIDOS, PedidoEvento.class);

    /** Longer than the producer's delivery.timeout.ms, so the producer gives up first (no stale retries). */
    private static final long ESPERA_CONFIRMACION_SEGUNDOS = 15;

    private final EventoOutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;

    /** Only to log the moments when Kafka goes down and comes back, not every failed round. */
    private boolean kafkaCaido = false;

    public RelevoOutbox(EventoOutboxRepository outbox, KafkaTemplate<String, Object> kafka, ObjectMapper json) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
    }

    /** Runs 1 s after the previous round finished (fixedDelay: two rounds never overlap). */
    @Scheduled(fixedDelayString = "${tienda.outbox.intervalo-ms:1000}")
    public void enviarPendientes() {
        List<EventoOutbox> pendientes = outbox.findTop100ByEnviadoEnIsNullOrderByIdAsc();
        for (EventoOutbox evento : pendientes) {
            if (!enviar(evento)) {
                return;   // keep the order: never send a later event before an earlier one
            }
        }
    }

    /** @return true if Kafka confirmed the event */
    boolean enviar(EventoOutbox evento) {
        try {
            Object mensaje = json.readValue(evento.getPayload(), CLASES.get(evento.getTopico()));
            SendResult<String, Object> resultado = kafka.send(evento.getTopico(), evento.getClave(), mensaje)
                    .get(ESPERA_CONFIRMACION_SEGUNDOS, TimeUnit.SECONDS);

            evento.marcarEnviado();
            outbox.save(evento);
            if (kafkaCaido) {
                kafkaCaido = false;
                log.info("Kafka disponible de nuevo: enviando los eventos que quedaron pendientes en el outbox");
            }
            log.info("--> Publicado {} en '{}' [particion {}, offset {}] clave={} (outbox #{})", evento.getTipo(),
                    evento.getTopico(), resultado.getRecordMetadata().partition(),
                    resultado.getRecordMetadata().offset(), evento.getClave(), evento.getId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            if (!kafkaCaido) {
                kafkaCaido = true;
                log.warn("No se pudo publicar en Kafka ({}). Los eventos quedan guardados en el outbox "
                        + "y se reintentan cada segundo. Pendientes: {}", causa(e), outbox.countByEnviadoEnIsNull());
            }
            return false;
        }
    }

    /** Housekeeping, every day at 3 a.m.: deletes events sent more than 7 days ago so the table does not grow forever. */
    @Scheduled(cron = "0 0 3 * * *")
    public void limpiarEnviados() {
        long borrados = outbox.deleteByEnviadoEnBefore(Instant.now().minus(Duration.ofDays(7)));
        log.info("Outbox: {} eventos enviados hace mas de 7 dias fueron borrados", borrados);
    }

    private static String causa(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
    }
}
