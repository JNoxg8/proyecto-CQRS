package com.tienda.autenticacion.api;

import com.tienda.autenticacion.aplicacion.AutenticacionServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point of the identity provider: sign-up and login.
 * Both endpoints are public (see SeguridadConfig) and return a {@link RespuestaSesion} with the JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final AutenticacionServicio servicio;

    public AutenticacionController(AutenticacionServicio servicio) {
        this.servicio = servicio;
    }

    /**
     * POST /api/auth/registro -> 201 Created. Creates a CLIENTE account and logs it in.
     * @param solicitud name, e-mail and password (validated: 400 if invalid)
     * @return the session with the signed token; 409 if the e-mail is already registered
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSesion registrar(@Valid @RequestBody RegistroSolicitud solicitud) {
        return servicio.registrar(solicitud);
    }

    /**
     * POST /api/auth/login -> 200 OK with a new token, or 401 if the credentials are wrong.
     * @param solicitud e-mail and password
     * @return the session with the signed token
     */
    @PostMapping("/login")
    public RespuestaSesion iniciarSesion(@Valid @RequestBody LoginSolicitud solicitud) {
        return servicio.iniciarSesion(solicitud);
    }
}
