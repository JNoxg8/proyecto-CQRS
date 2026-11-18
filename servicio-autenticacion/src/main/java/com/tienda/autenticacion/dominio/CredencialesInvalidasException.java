package com.tienda.autenticacion.dominio;

/**
 * Wrong e-mail OR wrong password. The message is the same for both cases on purpose: telling them apart
 * would let an attacker discover which e-mails are registered (user enumeration).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Correo o contraseña incorrectos");
    }
}
