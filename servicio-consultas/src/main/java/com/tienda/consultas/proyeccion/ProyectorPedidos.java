package com.tienda.consultas.proyeccion;

import com.tienda.consultas.modelo.PedidoVista;
import com.tienda.consultas.repositorio.PedidoVistaRepository;
import com.tienda.consultas.tiemporeal.NotificadorTiempoReal;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.Topicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.stereotype.Component;

/** PROJECTION: turns order events into the denormalized "pedidos" read model. */
@Component
public class ProyectorPedidos extends AbstractConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(ProyectorPedidos.class);

    private final PedidoVistaRepository pedidos;
    private final NotificadorTiempoReal tiempoReal;

    public ProyectorPedidos(PedidoVistaRepository pedidos, NotificadorTiempoReal tiempoReal) {
        this.pedidos = pedidos;
        this.tiempoReal = tiempoReal;
    }

    @KafkaListener(topics = Topicos.PEDIDOS, groupId = "consultas-pedidos")
    public void proyectar(PedidoEvento evento) {
        pedidos.save(PedidoVista.desde(evento));
        log.info("<-- {} pedido #{} proyectado en Mongo (estado {})", evento.tipo(), evento.pedidoId(), evento.estado());
        tiempoReal.enviarPedido(evento);   // only to admins and to the owner of the order
    }
}
