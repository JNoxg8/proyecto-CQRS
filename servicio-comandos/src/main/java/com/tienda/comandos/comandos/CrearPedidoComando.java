package com.tienda.comandos.comandos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Command: "I want to buy N units of this shoe".
 * There is no name or e-mail here on purpose: the buyer is the user of the token.
 */
public record CrearPedidoComando(
        @NotNull(message = "Debes indicar el calzado") Long calzadoId,
        @Positive(message = "La cantidad debe ser mayor que cero")
        @Max(value = 10, message = "Máximo 10 unidades por pedido") int cantidad) {
}
