package com.tienda.autenticacion.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/registro. There is no "rol" field: sign-up always creates a CLIENTE. */
public record RegistroSolicitud(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido") @Size(max = 120) String email,
        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password) {
}
