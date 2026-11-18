package com.tienda.comandos.aplicacion;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Who is sending the command, taken from the VERIFIED token.
 * The identity never comes from the request body: otherwise anyone could buy in someone else's name.
 */
public record UsuarioActual(String email, String nombre, boolean esAdmin) {

    /** Builds the user from the claims "sub" (e-mail), "nombre" and "roles". */
    public static UsuarioActual desde(Jwt token) {
        List<String> roles = token.getClaimAsStringList("roles");
        return new UsuarioActual(token.getSubject(), token.getClaimAsString("nombre"),
                roles != null && roles.contains("ADMIN"));
    }
}
