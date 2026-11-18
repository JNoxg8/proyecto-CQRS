package com.tienda.autenticacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Identity provider of the solution: "who are you?" (authentication) and "what role do you have?".
 * What each role is allowed to do (authorization) is decided by each service, reading the token.
 *
 * <p>UserDetailsServiceAutoConfiguration is excluded: otherwise Spring Boot creates a default "user" with a
 * random password that we never use (one less unnecessary door). Our users live in the usuarios table.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ServicioAutenticacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioAutenticacionApplication.class, args);
    }
}
