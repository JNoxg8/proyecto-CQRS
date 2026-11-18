package com.tienda.consultas.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * When a browser closes the tab, the next write on its live (SSE) connection fails with an IOException.
 * That is normal, not an error: without this handler Tomcat would print a full ERROR stack trace each time.
 */
@RestControllerAdvice
public class ManejadorDesconexiones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDesconexiones.class);

    /** Logs the disconnection at DEBUG level and does nothing else. */
    @ExceptionHandler(IOException.class)
    public void clienteDesconectado(IOException ex) {
        log.debug("Un navegador cerró su conexión en vivo: {}", ex.getMessage());
    }
}
