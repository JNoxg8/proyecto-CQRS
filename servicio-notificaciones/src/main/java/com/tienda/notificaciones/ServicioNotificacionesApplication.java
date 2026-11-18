package com.tienda.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Independent consumer. It subscribes to the SAME topics as servicio-consultas but with its own consumer
 * group ("notificaciones"), so Kafka delivers every event to BOTH services (publish/subscribe).
 * The producer (servicio-comandos) does not even know this service exists.
 */
@SpringBootApplication
public class ServicioNotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioNotificacionesApplication.class, args);
    }
}
