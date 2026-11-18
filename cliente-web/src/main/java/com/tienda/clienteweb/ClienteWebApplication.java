package com.tienda.clienteweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Serves the client apps (src/main/resources/static). The pages run in the browser and talk directly to:
 *   - servicio-autenticacion (http://localhost:8083) to log in / sign up and get the JWT
 *   - servicio-comandos  (http://localhost:8081) to send commands
 *   - servicio-consultas (http://localhost:8082) to read data and receive live events
 */
@SpringBootApplication
public class ClienteWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClienteWebApplication.class, args);
    }
}
