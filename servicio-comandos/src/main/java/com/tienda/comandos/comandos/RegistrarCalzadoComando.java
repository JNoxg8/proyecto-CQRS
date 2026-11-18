package com.tienda.comandos.comandos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Command: "register this shoe in the inventory". Bean Validation rejects bad input with 400 before any logic runs. */
public record RegistrarCalzadoComando(
        @NotBlank(message = "La marca es obligatoria") @Size(max = 40) String marca,
        @NotBlank(message = "El modelo es obligatorio") @Size(max = 60) String modelo,
        @NotBlank(message = "La categoría es obligatoria") @Size(max = 30) String categoria,
        @DecimalMin(value = "15.0", message = "La talla mínima es 15")
        @DecimalMax(value = "50.0", message = "La talla máxima es 50") double talla,
        @NotBlank(message = "El color es obligatorio") @Size(max = 30) String color,
        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El precio admite máximo 2 decimales") BigDecimal precio,
        @PositiveOrZero(message = "El stock inicial no puede ser negativo")
        @Max(value = 1000, message = "El stock inicial máximo es 1000") int stockInicial) {
}
