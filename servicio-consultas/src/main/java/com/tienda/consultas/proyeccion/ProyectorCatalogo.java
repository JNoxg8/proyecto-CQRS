package com.tienda.consultas.proyeccion;

import com.tienda.consultas.modelo.CalzadoVista;
import com.tienda.consultas.repositorio.CalzadoVistaRepository;
import com.tienda.consultas.tiemporeal.NotificadorTiempoReal;
import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.Topicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.stereotype.Component;

/**
 * PROJECTION: turns shoe events into the "catalogo" read model.
 *
 * <p>The event carries the full state, so the projection is a simple upsert (save = insert or replace by id).
 * Applying the same event twice gives the same document -> it is <b>idempotent</b>, which makes replays safe.
 *
 * <p>Extends {@link AbstractConsumerSeekAware} so the admin can "rewind" the topic and rebuild the catalog.
 */
@Component
public class ProyectorCatalogo extends AbstractConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(ProyectorCatalogo.class);

    private final CalzadoVistaRepository catalogo;
    private final NotificadorTiempoReal tiempoReal;

    public ProyectorCatalogo(CalzadoVistaRepository catalogo, NotificadorTiempoReal tiempoReal) {
        this.catalogo = catalogo;
        this.tiempoReal = tiempoReal;
    }

    /** Consumer group "consultas-catalogo": upserts the shoe and pushes it to every browser. */
    @KafkaListener(topics = Topicos.CALZADOS, groupId = "consultas-catalogo")
    public void proyectar(CalzadoEvento evento) {
        catalogo.save(CalzadoVista.desde(evento));
        log.info("<-- {} calzado #{} proyectado en Mongo (stock {})", evento.tipo(), evento.calzadoId(), evento.stock());
        tiempoReal.enviarATodos("calzado", evento);   // public: the catalog is visible to everyone
    }
}
