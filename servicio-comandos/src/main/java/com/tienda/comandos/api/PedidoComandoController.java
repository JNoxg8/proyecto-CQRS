package com.tienda.comandos.api;

import com.tienda.comandos.aplicacion.PedidoServicio;
import com.tienda.comandos.aplicacion.UsuarioActual;
import com.tienda.comandos.comandos.CrearPedidoComando;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Order commands. Who may call each one is defined in SeguridadConfig. */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoComandoController {

    private final PedidoServicio servicio;

    public PedidoComandoController(PedidoServicio servicio) {
        this.servicio = servicio;
    }

    /** @AuthenticationPrincipal gives us the token that Spring Security already verified. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaComando crear(@Valid @RequestBody CrearPedidoComando comando, @AuthenticationPrincipal Jwt token) {
        Long id = servicio.crear(comando, UsuarioActual.desde(token));
        return new RespuestaComando(id, "Pedido #" + id + " creado");
    }

    /** POST /api/pedidos/{id}/despachar (ADMIN). Only orders in state CREADO can be shipped (409 otherwise). */
    @PostMapping("/{id}/despachar")
    public RespuestaComando despachar(@PathVariable Long id) {
        servicio.despachar(id);
        return new RespuestaComando(id, "Pedido #" + id + " despachado");
    }

    /** POST /api/pedidos/{id}/cancelar (CLIENTE: own orders only, ADMIN: any). Returns the units to the stock. */
    @PostMapping("/{id}/cancelar")
    public RespuestaComando cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        servicio.cancelar(id, UsuarioActual.desde(token));
        return new RespuestaComando(id, "Pedido #" + id + " cancelado");
    }
}
