package com.tienda.consultas.api;

import com.tienda.consultas.modelo.PedidoVista;
import com.tienda.consultas.repositorio.PedidoVistaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Order queries. Who may call each one is defined in SeguridadConfig. */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoConsultaController {

    private final PedidoVistaRepository pedidos;

    public PedidoConsultaController(PedidoVistaRepository pedidos) {
        this.pedidos = pedidos;
    }

    /**
     * The orders of the logged-in user. The e-mail comes from the token, never from a parameter:
     * with "?email=..." anyone could read other people's orders just by changing the e-mail (IDOR).
     */
    @GetMapping("/mios")
    public List<PedidoVista> misPedidos(@AuthenticationPrincipal Jwt token) {
        return pedidos.findByClienteEmailOrderByCreadoEnDesc(token.getSubject().toLowerCase());
    }

    /** ADMIN: every order. */
    @GetMapping
    public List<PedidoVista> todos() {
        return pedidos.findAll(Sort.by(Sort.Direction.DESC, "creadoEn"));
    }

    /** ADMIN: one order. */
    @GetMapping("/{id}")
    public PedidoVista porId(@PathVariable Long id) {
        return pedidos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el pedido " + id));
    }
}
