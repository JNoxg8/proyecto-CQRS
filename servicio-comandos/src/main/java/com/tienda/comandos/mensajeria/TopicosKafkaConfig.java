package com.tienda.comandos.mensajeria;

import com.tienda.eventos.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The write side owns the events, so it declares the topics (Kafka auto-create is disabled in docker-compose).
 * 3 partitions: events of different shoes can be processed in parallel, while the key keeps the order
 * of the events of the same shoe.
 */
@Configuration
public class TopicosKafkaConfig {

    /** Topic calzado-eventos (created on startup if it does not exist). */
    @Bean
    public NewTopic topicoCalzados() {
        return TopicBuilder.name(Topicos.CALZADOS).partitions(3).replicas(1).build();
    }

    /** Topic pedido-eventos (created on startup if it does not exist). */
    @Bean
    public NewTopic topicoPedidos() {
        return TopicBuilder.name(Topicos.PEDIDOS).partitions(3).replicas(1).build();
    }
}
