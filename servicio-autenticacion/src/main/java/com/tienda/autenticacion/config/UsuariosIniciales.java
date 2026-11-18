package com.tienda.autenticacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Demo accounts read from application.yml (tienda.usuarios-iniciales.*). */
@ConfigurationProperties(prefix = "tienda.usuarios-iniciales")
public record UsuariosIniciales(Cuenta admin, Cuenta cliente) {

    public record Cuenta(String nombre, String email, String password) {
    }
}
