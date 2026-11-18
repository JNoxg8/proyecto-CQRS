package com.tienda.consultas.tiemporeal;

import com.tienda.eventos.PedidoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the browsers connected by Server-Sent Events (SSE) and pushes the projected events to them.
 *
 * <p>Privacy: shoe events go to everybody (public catalog), but an order event (with name and e-mail)
 * only goes to the admins and to the customer who owns that order.
 *
 * <p>Why SSE and not WebSocket: the flow is one-way (server -> browser), SSE works over plain HTTP and the
 * browser's {@code EventSource} reconnects automatically. WebSocket is for two-way channels (e.g. a chat).
 */
@Component
public class NotificadorTiempoReal {

    private static final Logger log = LoggerFactory.getLogger(NotificadorTiempoReal.class);
    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();   // then the browser reconnects

    /** One open connection. email == null means an anonymous visitor. */
    private record Suscriptor(SseEmitter emisor, String email, boolean esAdmin) {

        boolean puedeVer(PedidoEvento pedido) {
            return esAdmin || (email != null && email.equalsIgnoreCase(pedido.clienteEmail()));
        }
    }

    private final List<Suscriptor> suscriptores = new CopyOnWriteArrayList<>();

    public SseEmitter suscribir(String email, boolean esAdmin) {
        SseEmitter emisor = new SseEmitter(TIMEOUT);
        Suscriptor suscriptor = new Suscriptor(emisor, email, esAdmin);
        suscriptores.add(suscriptor);
        emisor.onCompletion(() -> suscriptores.remove(suscriptor));
        emisor.onTimeout(() -> suscriptores.remove(suscriptor));
        emisor.onError(error -> suscriptores.remove(suscriptor));
        enviarA(suscriptor, SseEmitter.event().name("conectado").data("ok"));
        log.info("Conexión en vivo abierta por {} (total: {})",
                email == null ? "un visitante" : email + (esAdmin ? " [ADMIN]" : ""), suscriptores.size());
        return emisor;
    }

    /** Public events (shoes, rebuild): to every connection. */
    public void enviarATodos(String nombre, Object datos) {
        for (Suscriptor s : suscriptores) {
            enviarA(s, SseEmitter.event().name(nombre).data(datos));
        }
    }

    /** Order events: only to admins and to the owner of the order. */
    public void enviarPedido(PedidoEvento pedido) {
        for (Suscriptor s : suscriptores) {
            if (s.puedeVer(pedido)) {
                enviarA(s, SseEmitter.event().name("pedido").data(pedido));
            }
        }
    }

    /** A comment every 20 s keeps idle connections alive and detects browsers that already left. */
    @Scheduled(fixedRate = 20_000)
    public void latido() {
        for (Suscriptor s : suscriptores) {
            enviarA(s, SseEmitter.event().comment("latido"));
        }
    }

    // synchronized: Kafka listener threads and the scheduler may write to the same connection at once
    private synchronized void enviarA(Suscriptor suscriptor, SseEmitter.SseEventBuilder evento) {
        try {
            suscriptor.emisor().send(evento);
        } catch (Exception e) {
            suscriptores.remove(suscriptor);   // the browser closed the tab
        }
    }
}
