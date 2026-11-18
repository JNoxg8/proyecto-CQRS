package com.tienda.comandos.dominio;

/** The shoe or order does not exist. The API answers 404 Not Found. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
