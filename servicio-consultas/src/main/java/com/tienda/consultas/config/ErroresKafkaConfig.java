package com.tienda.consultas.config;

import com.tienda.eventos.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fault tolerance of the Kafka consumers: <b>retries with exponential back-off + Dead Letter Topic</b>.
 *
 * <p>Without this, Spring Kafka retries a failing message 10 times in a row (no pause) and then DISCARDS it:
 * if MongoDB were down for a minute, those events would be lost and the read model would stay out of date.
 *
 * <ul>
 *   <li><b>Temporary failure</b> (MongoDB down, network): retry after 1 s, 2 s, 4 s, 8 s, then every 10 s,
 *       for about 2 minutes. The partition waits meanwhile, so the order of the events is kept.
 *       If the problem is fixed in time, the event is processed normally and nothing is lost.</li>
 *   <li><b>Permanent failure</b> (still failing after the retries, or a corrupt message that can never be
 *       read): the message is copied to {@code <topic>.DLT} with headers that say why it failed and which
 *       consumer group failed, and the consumer moves on. Nothing is lost silently: it can be inspected in
 *       Kafka UI, and the read model can be rebuilt from the original topic.</li>
 * </ul>
 * Spring Boot detects the {@link DefaultErrorHandler} bean and plugs it into every {@code @KafkaListener}.
 */
@Configuration
public class ErroresKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(ErroresKafkaConfig.class);

    /** Same 3 partitions as the original topic: the record goes to the same partition number in the DLT. */
    @Bean
    public NewTopic dltCalzados() {
        return TopicBuilder.name(Topicos.CALZADOS_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic dltPedidos() {
        return TopicBuilder.name(Topicos.PEDIDOS_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler manejadorErroresKafka(ProducerFactory<?, ?> fabricaProductores) {
        DeadLetterPublishingRecoverer haciaDlt = new DeadLetterPublishingRecoverer(plantillaDlt(fabricaProductores));

        ExponentialBackOff espera = new ExponentialBackOff(1_000, 2.0);
        espera.setMaxInterval(10_000);      // never wait more than 10 s between two attempts
        espera.setMaxElapsedTime(120_000);  // give up after ~2 minutes of waiting -> DLT

        // Corrupt messages (DeserializationException, ClassCastException...) are NOT retried by default:
        // retrying cannot fix them, so they go straight to the DLT.
        DefaultErrorHandler manejador = new DefaultErrorHandler(haciaDlt, espera);
        manejador.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> registro, Exception ex, int intento) {
                log.warn("Fallo procesando {}-{}@{} (intento {}): {}. Se reintentara con espera.", registro.topic(),
                        registro.partition(), registro.offset(), intento, causa(ex));
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> registro, Exception ex) {
                log.error("Mensaje {}-{}@{} enviado a {}.DLT: {}", registro.topic(), registro.partition(),
                        registro.offset(), registro.topic(), causa(ex));
            }
        });
        return manejador;
    }

    /**
     * Producer used only to write into the DLTs. A normal message is written as JSON; a corrupt one arrives as
     * the original raw bytes and is copied as they are (that is why the serializer depends on the value type).
     */
    private static KafkaTemplate<String, Object> plantillaDlt(ProducerFactory<?, ?> fabricaProductores) {
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());   // checked first
        porTipo.put(Object.class, new JsonSerializer<>());
        Map<String, Object> configuracion = new HashMap<>(fabricaProductores.getConfigurationProperties());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configuracion,
                new StringSerializer(), new DelegatingByTypeSerializer(porTipo, true)));
    }

    private static String causa(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
    }
}
