package com.tienda.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * READ side of the CQRS solution.
 * It never receives commands: its data only changes when an event arrives from Kafka.
 */
@SpringBootApplication
@EnableScheduling   // heartbeat of the live connections (see NotificadorTiempoReal)
public class ServicioConsultasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioConsultasApplication.class, args);
    }
}
