package com.tienda.autenticacion.api;

import com.tienda.autenticacion.aplicacion.AutenticacionServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final AutenticacionServicio servicio;

    public AutenticacionController(AutenticacionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSesion registrar(@Valid @RequestBody RegistroSolicitud solicitud) {
        return servicio.registrar(solicitud);
    }

    @PostMapping("/login")
    public RespuestaSesion iniciarSesion(@Valid @RequestBody LoginSolicitud solicitud) {
        return servicio.iniciarSesion(solicitud);
    }
}
