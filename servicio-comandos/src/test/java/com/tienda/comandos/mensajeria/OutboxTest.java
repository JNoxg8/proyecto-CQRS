package com.tienda.comandos.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.TipoEventoCalzado;
import com.tienda.eventos.Topicos;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Transactional Outbox: what is stored, in which order it is sent, and what happens when Kafka is down. */
class OutboxTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private EventoOutboxRepository repositorio;
    private KafkaTemplate<String, Object> kafka;
    private RelevoOutbox relevo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        repositorio = mock(EventoOutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
        relevo = new RelevoOutbox(repositorio, kafka, json);
    }

    private static CalzadoEvento evento(long id, int stock) {
        return new CalzadoEvento(TipoEventoCalzado.STOCK_DESCONTADO, id, "Crocs", "Classic Clog", "sandalias",
                37, "verde", new BigDecimal("229900"), stock, Instant.parse("2026-11-23T10:00:00Z"));
    }

    private EventoOutbox fila(CalzadoEvento e) throws Exception {
        return new EventoOutbox(Topicos.CALZADOS, String.valueOf(e.calzadoId()), e.tipo().name(), json.writeValueAsString(e));
    }

    private static CompletableFuture<SendResult<String, Object>> confirmado(String topico) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topico, 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(topico, "k", "v"), metadata));
    }

    @Test
    void elRegistradorGuardaElEventoComoPendienteConSuTopicoYClave() {
        new RegistradorOutbox(repositorio, json).registrar(evento(7, 3));

        ArgumentCaptor<EventoOutbox> guardado = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(repositorio).save(guardado.capture());
        assertThat(guardado.getValue().getTopico()).isEqualTo(Topicos.CALZADOS);
        assertThat(guardado.getValue().getClave()).isEqualTo("7");
        assertThat(guardado.getValue().getTipo()).isEqualTo("STOCK_DESCONTADO");
        assertThat(guardado.getValue().estaPendiente()).isTrue();
        verifyNoInteractions(kafka);   // buying never talks to Kafka
    }

    @Test
    void elRelevoEnviaEnOrdenElMismoEventoYLoMarcaComoEnviado() throws Exception {
        EventoOutbox primero = fila(evento(7, 3));
        EventoOutbox segundo = fila(evento(8, 1));
        when(repositorio.findTop100ByEnviadoEnIsNullOrderByIdAsc()).thenReturn(List.of(primero, segundo));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(confirmado(Topicos.CALZADOS));

        relevo.enviarPendientes();

        InOrder orden = inOrder(kafka);
        orden.verify(kafka).send(Topicos.CALZADOS, "7", evento(7, 3));   // the JSON becomes the same record again
        orden.verify(kafka).send(Topicos.CALZADOS, "8", evento(8, 1));
        assertThat(primero.estaPendiente()).isFalse();
        assertThat(segundo.estaPendiente()).isFalse();
        verify(repositorio, times(2)).save(any());
    }

    @Test
    void siKafkaEstaCaidoElEventoQuedaPendienteYNoSeSaltaAlSiguiente() throws Exception {
        EventoOutbox primero = fila(evento(7, 3));
        EventoOutbox segundo = fila(evento(8, 1));
        when(repositorio.findTop100ByEnviadoEnIsNullOrderByIdAsc()).thenReturn(List.of(primero, segundo));
        when(kafka.send(anyString(), eq("7"), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Kafka no responde")));

        relevo.enviarPendientes();

        assertThat(primero.estaPendiente()).isTrue();             // kept for the next round
        verify(kafka, never()).send(anyString(), eq("8"), any());  // order preserved: the later one waits
        verify(repositorio, never()).save(any());
    }
}
