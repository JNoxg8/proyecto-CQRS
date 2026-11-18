package com.tienda.consultas.api;

import com.tienda.consultas.aplicacion.CatalogoConsultas;
import com.tienda.consultas.modelo.CalzadoVista;
import com.tienda.consultas.repositorio.CalzadoVistaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** Catalog queries: GET /api/catalogo?marca=Nike&categoria=deportivo&talla=40&precioMax=500000&soloDisponibles=true */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final CatalogoConsultas consultas;
    private final CalzadoVistaRepository catalogo;

    public CatalogoController(CatalogoConsultas consultas, CalzadoVistaRepository catalogo) {
        this.consultas = consultas;
        this.catalogo = catalogo;
    }

    @GetMapping
    public List<CalzadoVista> buscar(@RequestParam(required = false) String marca,
                                     @RequestParam(required = false) String categoria,
                                     @RequestParam(required = false) Double talla,
                                     @RequestParam(required = false) BigDecimal precioMax,
                                     @RequestParam(defaultValue = "false") boolean soloDisponibles) {
        return consultas.buscar(marca, categoria, talla, precioMax, soloDisponibles);
    }

    @GetMapping("/{id}")
    public CalzadoVista porId(@PathVariable Long id) {
        return catalogo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el calzado " + id));
    }
}
