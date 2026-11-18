package com.tienda.autenticacion.api;

import jakarta.validation.constraints.NotBlank;

public record LoginSolicitud(
        @NotBlank(message = "El correo es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
