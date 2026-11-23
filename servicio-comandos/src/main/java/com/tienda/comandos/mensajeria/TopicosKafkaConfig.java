package com.tienda.comandos.mensajeria;

import com.tienda.eventos.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The write side owns the events, so it declares the topics (Kafka auto-create is disabled in docker-compose).
 * 3 partitions: events of different shoes can be processed in parallel, while the key keeps the order
 * of the events of the same shoe.
 *
 * <p>retention.ms = -1 (keep forever): by default Kafka deletes messages older than 7 days. Here the topics
 * are the history used to rebuild the read model, so losing old events would make the rebuild incomplete.
 * {@code spring.kafka.admin.modify-topic-configs=true} applies it to topics that already exist.
 */
@Configuration
public class TopicosKafkaConfig {

    private static final String PARA_SIEMPRE = "-1";

    /** Topic calzado-eventos (created on startup if it does not exist). */
    @Bean
    public NewTopic topicoCalzados() {
        return TopicBuilder.name(Topicos.CALZADOS).partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, PARA_SIEMPRE).build();
    }

    /** Topic pedido-eventos (created on startup if it does not exist). */
    @Bean
    public NewTopic topicoPedidos() {
        return TopicBuilder.name(Topicos.PEDIDOS).partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, PARA_SIEMPRE).build();
    }
}
