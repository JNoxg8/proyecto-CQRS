package com.tienda.comandos.api;

/**
 * What a command returns: only the id and a message, never the full data.
 * In CQRS the client reads the data from the query side.
 */
public record RespuestaComando(Long id, String mensaje) {
}
