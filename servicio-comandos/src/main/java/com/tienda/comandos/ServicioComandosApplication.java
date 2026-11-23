package com.tienda.comandos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * WRITE side of the CQRS solution.
 * It only accepts commands (POST/PUT). It has no GET endpoints on purpose: reading is the job of servicio-consultas.
 * {@code @EnableScheduling} turns on the outbox relay ({@code RelevoOutbox}), which sends the events to Kafka.
 */
@SpringBootApplication
@EnableScheduling
public class ServicioComandosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioComandosApplication.class, args);
    }
}
