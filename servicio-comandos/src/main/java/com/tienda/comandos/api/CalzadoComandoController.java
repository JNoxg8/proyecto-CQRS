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

    /**
     * POST /api/calzados -> 201. Registers a new shoe (ADMIN).
     * @return the new id; the data itself will appear in the catalog when the event is projected
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaComando registrar(@Valid @RequestBody RegistrarCalzadoComando comando) {
        Long id = servicio.registrar(comando);
        return new RespuestaComando(id, "Calzado #" + id + " registrado");
    }

    /** PUT /api/calzados/{id}/precio (ADMIN). 404 if the shoe does not exist, 409 if the price is not positive. */
    @PutMapping("/{id}/precio")
    public RespuestaComando cambiarPrecio(@PathVariable Long id, @Valid @RequestBody CambiarPrecioComando comando) {
        servicio.cambiarPrecio(id, comando);
        return new RespuestaComando(id, "Precio del calzado #" + id + " actualizado");
    }

    /** POST /api/calzados/{id}/reponer-stock (ADMIN). Adds units to the stock. */
    @PostMapping("/{id}/reponer-stock")
    public RespuestaComando reponerStock(@PathVariable Long id, @Valid @RequestBody ReponerStockComando comando) {
        servicio.reponerStock(id, comando);
        return new RespuestaComando(id, "Se repusieron " + comando.cantidad() + " unidades al calzado #" + id);
    }
}
