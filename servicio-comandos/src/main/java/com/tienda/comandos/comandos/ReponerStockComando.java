package com.tienda.comandos.comandos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** Command: "add these units to the stock". */
public record ReponerStockComando(
        @Positive(message = "La cantidad a reponer debe ser mayor que cero")
        @Max(value = 1000, message = "Máximo 1000 unidades por reposición") int cantidad) {
}
