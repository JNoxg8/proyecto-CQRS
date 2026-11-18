package com.tienda.comandos.config;

import com.tienda.comandos.aplicacion.CalzadoServicio;
import com.tienda.comandos.comandos.RegistrarCalzadoComando;
import com.tienda.comandos.repositorio.CalzadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Loads a sample inventory the first time (empty table). It goes through the normal command service,
 * so every shoe also produces a CALZADO_REGISTRADO event and the read side builds its catalog by itself.
 */
@Component
@ConditionalOnProperty(name = "tienda.datos-iniciales", havingValue = "true")
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final CalzadoRepository calzados;
    private final CalzadoServicio servicio;

    public DatosIniciales(CalzadoRepository calzados, CalzadoServicio servicio) {
        this.calzados = calzados;
        this.servicio = servicio;
    }

    @Override
    public void run(String... args) {
        if (calzados.count() > 0) {
            return;
        }
        List<RegistrarCalzadoComando> inventario = List.of(
                new RegistrarCalzadoComando("Nike", "Air Max 90", "deportivo", 40, "blanco", new BigDecimal("459900"), 8),
                new RegistrarCalzadoComando("Adidas", "Samba OG", "casual", 39, "negro", new BigDecimal("389900"), 5),
                new RegistrarCalzadoComando("New Balance", "574", "deportivo", 40.5, "gris", new BigDecimal("419900"), 4),
                new RegistrarCalzadoComando("Puma", "Suede Classic", "casual", 38, "azul", new BigDecimal("279900"), 6),
                new RegistrarCalzadoComando("Vélez", "Oxford Clásico", "formal", 41, "café", new BigDecimal("329900"), 3),
                new RegistrarCalzadoComando("Timberland", "6-Inch Premium", "botas", 42, "trigo", new BigDecimal("899900"), 2),
                new RegistrarCalzadoComando("Crocs", "Classic Clog", "sandalias", 37, "verde", new BigDecimal("229900"), 1));
        inventario.forEach(servicio::registrar);
        log.info("Inventario inicial cargado: {} calzados (cada uno publicó un evento CALZADO_REGISTRADO)", inventario.size());
    }
}
