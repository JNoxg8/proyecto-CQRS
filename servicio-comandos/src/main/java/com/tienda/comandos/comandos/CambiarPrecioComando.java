package com.tienda.comandos.comandos;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Command: "change the price of this shoe". */
public record CambiarPrecioComando(
        @NotNull(message = "El nuevo precio es obligatorio")
        @Positive(message = "El precio debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El precio admite máximo 2 decimales") BigDecimal nuevoPrecio) {
}
