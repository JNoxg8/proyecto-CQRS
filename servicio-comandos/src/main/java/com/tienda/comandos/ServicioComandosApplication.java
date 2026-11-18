package com.tienda.comandos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WRITE side of the CQRS solution.
 * It only accepts commands (POST/PUT). It has no GET endpoints on purpose: reading is the job of servicio-consultas.
 */
@SpringBootApplication
public class ServicioComandosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioComandosApplication.class, args);
    }
}
