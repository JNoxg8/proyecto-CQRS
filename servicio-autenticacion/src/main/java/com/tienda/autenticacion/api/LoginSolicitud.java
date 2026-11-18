package com.tienda.autenticacion.api;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/auth/login. */
public record LoginSolicitud(
        @NotBlank(message = "El correo es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
