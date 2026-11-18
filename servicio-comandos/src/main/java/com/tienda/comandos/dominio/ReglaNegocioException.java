package com.tienda.comandos.dominio;

/** A business rule was violated (e.g. not enough stock). The API answers 409 Conflict. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
