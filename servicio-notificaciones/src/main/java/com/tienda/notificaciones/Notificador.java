package com.tienda.notificaciones;

import com.tienda.eventos.CalzadoEvento;
import com.tienda.eventos.PedidoEvento;
import com.tienda.eventos.Topicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Reacts to the events in its own way. The e-mails are SIMULATED (written to the log);
 * a real system would call an e-mail provider here.
 */
@Component
public class Notificador {

    private static final Logger log = LoggerFactory.getLogger(Notificador.class);
    private static final int STOCK_BAJO = 2;

    /** Simulates the e-mail that the customer would receive for each order change. */
    @KafkaListener(topics = Topicos.PEDIDOS, groupId = "notificaciones")
    public void alCambiarPedido(PedidoEvento e) {
        String mensaje = switch (e.tipo()) {
            case PEDIDO_CREADO -> "Hola " + e.clienteNombre() + ", recibimos tu pedido #" + e.pedidoId() + ": "
                    + e.cantidad() + " x " + e.descripcionCalzado() + ". Total " + pesos(e.total()) + ".";
            case PEDIDO_DESPACHADO -> "Hola " + e.clienteNombre() + ", tu pedido #" + e.pedidoId() + " va en camino.";
            case PEDIDO_CANCELADO -> "Hola " + e.clienteNombre() + ", tu pedido #" + e.pedidoId() + " fue cancelado.";
        };
        log.info("[CORREO SIMULADO] Para: {} | {}", e.clienteEmail(), mensaje);
    }

    /** Warns the store when a shoe runs out or has {@value #STOCK_BAJO} units or fewer. */
    @KafkaListener(topics = Topicos.CALZADOS, groupId = "notificaciones")
    public void alCambiarCalzado(CalzadoEvento e) {
        String calzado = e.marca() + " " + e.modelo() + " (#" + e.calzadoId() + ")";
        if (e.stock() == 0) {
            log.warn("[ALERTA INVENTARIO] {} se AGOTO. Hay que reponer stock.", calzado);
        } else if (e.stock() <= STOCK_BAJO) {
            log.warn("[ALERTA INVENTARIO] {} tiene stock bajo: quedan {}.", calzado, e.stock());
        }
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        formato.setMaximumFractionDigits(0);
        return formato.format(valor);
    }
}
