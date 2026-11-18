package com.tienda.consultas.modelo;

import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.EstadoPedido;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.TipoEventoCalzado;
import com.tienda.eventos.TipoEventoPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks how events are turned into read-model documents (no Kafka or Mongo needed). */
class ProyeccionTest {

    private CalzadoEvento eventoConStock(int stock) {
        return new CalzadoEvento(TipoEventoCalzado.STOCK_DESCONTADO, 7L, "Crocs", "Classic Clog", "sandalias",
                37, "verde", new BigDecimal("229900"), stock, Instant.now());
    }

    @Test
    void calzadoConStockQuedaDisponible() {
        CalzadoVista vista = CalzadoVista.desde(eventoConStock(3));

        assertThat(vista.id()).isEqualTo(7L);
        assertThat(vista.disponible()).isTrue();
    }

    @Test
    void calzadoSinStockQuedaAgotado() {
        assertThat(CalzadoVista.desde(eventoConStock(0)).disponible()).isFalse();
    }

    @Test
    void elMismoEventoProduceElMismoDocumento() {
        CalzadoEvento evento = eventoConStock(2);

        // Idempotency: replaying an event gives exactly the same document
        assertThat(CalzadoVista.desde(evento)).isEqualTo(CalzadoVista.desde(evento));
    }

    @Test
    void elPedidoGuardaLaDescripcionDelCalzado() {
        PedidoEvento evento = new PedidoEvento(TipoEventoPedido.PEDIDO_CREADO, 15L, 7L,
                "Crocs Classic Clog - talla 37 - verde", 1, new BigDecimal("229900"), new BigDecimal("229900"),
                "Ana", "ana@correo.com", EstadoPedido.CREADO, Instant.now(), Instant.now());

        PedidoVista vista = PedidoVista.desde(evento);

        assertThat(vista.descripcionCalzado()).isEqualTo("Crocs Classic Clog - talla 37 - verde");
        assertThat(vista.estado()).isEqualTo(EstadoPedido.CREADO);
    }
}
