package com.tienda.comandos.api;

import com.tienda.comandos.aplicacion.CalzadoServicio;
import com.tienda.comandos.comandos.CambiarPrecioComando;
import com.tienda.comandos.comandos.RegistrarCalzadoComando;
import com.tienda.comandos.comandos.ReponerStockComando;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Inventory commands (used by the Admin client). */
@RestController
@RequestMapping("/api/calzados")
public class CalzadoComandoController {

    private final CalzadoServicio servicio;

    public CalzadoComandoController(CalzadoServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaComando registrar(@Valid @RequestBody RegistrarCalzadoComando comando) {
        Long id = servicio.registrar(comando);
        return new RespuestaComando(id, "Calzado #" + id + " registrado");
    }

    @PutMapping("/{id}/precio")
    public RespuestaComando cambiarPrecio(@PathVariable Long id, @Valid @RequestBody CambiarPrecioComando comando) {
        servicio.cambiarPrecio(id, comando);
        return new RespuestaComando(id, "Precio del calzado #" + id + " actualizado");
    }

    @PostMapping("/{id}/reponer-stock")
    public RespuestaComando reponerStock(@PathVariable Long id, @Valid @RequestBody ReponerStockComando comando) {
        servicio.reponerStock(id, comando);
        return new RespuestaComando(id, "Se repusieron " + comando.cantidad() + " unidades al calzado #" + id);
    }
}
