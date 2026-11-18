package com.tienda.autenticacion.dominio;

/** Someone tried to create a second account with the same e-mail. The API answers 409 Conflict. */
public class CorreoYaRegistradoException extends RuntimeException {

    public CorreoYaRegistradoException(String email) {
        super("Ya existe una cuenta con el correo " + email);
    }
}
