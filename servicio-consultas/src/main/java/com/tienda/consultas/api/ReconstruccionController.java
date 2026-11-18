package com.tienda.consultas.api;

import com.tienda.consultas.proyeccion.ProyectorCatalogo;
import com.tienda.consultas.proyeccion.ProyectorPedidos;
import com.tienda.consultas.repositorio.CalzadoVistaRepository;
import com.tienda.consultas.repositorio.PedidoVistaRepository;
import com.tienda.consultas.tiemporeal.NotificadorTiempoReal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Maintenance operation of the read side (not a business command): deletes the read model and replays
 * every event stored in Kafka from offset 0. It proves that in CQRS the read model is <b>disposable</b>:
 * it can always be rebuilt from the events.
 */
@RestController
@RequestMapping("/api/admin")
public class ReconstruccionController {

    private static final Logger log = LoggerFactory.getLogger(ReconstruccionController.class);

    private final CalzadoVistaRepository catalogo;
    private final PedidoVistaRepository pedidos;
    private final ProyectorCatalogo proyectorCatalogo;
    private final ProyectorPedidos proyectorPedidos;
    private final NotificadorTiempoReal tiempoReal;

    public ReconstruccionController(CalzadoVistaRepository catalogo, PedidoVistaRepository pedidos,
                                    ProyectorCatalogo proyectorCatalogo, ProyectorPedidos proyectorPedidos,
                                    NotificadorTiempoReal tiempoReal) {
        this.catalogo = catalogo;
        this.pedidos = pedidos;
        this.proyectorCatalogo = proyectorCatalogo;
        this.proyectorPedidos = proyectorPedidos;
        this.tiempoReal = tiempoReal;
    }

    @PostMapping("/reconstruir")
    @ResponseStatus(HttpStatus.ACCEPTED)   // 202: accepted, it finishes asynchronously while the events are replayed
    public Map<String, String> reconstruir() {
        catalogo.deleteAll();
        pedidos.deleteAll();
        tiempoReal.enviarATodos("reconstruccion", Map.of("estado", "modelo de lectura borrado"));
        // The seek is executed by each consumer thread on its next poll
        proyectorCatalogo.seekToBeginning();
        proyectorPedidos.seekToBeginning();
        log.warn("Modelo de lectura borrado: reproduciendo todos los eventos desde el offset 0");
        return Map.of("mensaje", "Modelo de lectura borrado. Reproduciendo los eventos de Kafka desde el inicio...");
    }
}
