package com.tienda.autenticacion.api;

import java.time.Instant;

/** What the client receives after login or sign-up. It must send {@code token} as "Authorization: Bearer ...". */
public record RespuestaSesion(String token, String tipo, Instant expiraEn, String nombre, String email, String rol) {
}
