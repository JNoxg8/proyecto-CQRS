package com.tienda.comandos.dominio;

/** The user is authenticated but this resource is not theirs (e.g. another customer's order). API: 403. */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
