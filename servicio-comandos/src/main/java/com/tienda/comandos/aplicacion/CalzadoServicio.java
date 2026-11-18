package com.tienda.comandos.aplicacion;

import com.tienda.comandos.comandos.CambiarPrecioComando;
import com.tienda.comandos.comandos.RegistrarCalzadoComando;
import com.tienda.comandos.comandos.ReponerStockComando;
import com.tienda.comandos.dominio.Calzado;
import com.tienda.comandos.dominio.RecursoNoEncontradoException;
import com.tienda.comandos.mensajeria.EventoMapper;
import com.tienda.comandos.repositorio.CalzadoRepository;
import com.tienda.eventos.TipoEventoCalzado;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the inventory commands. Each method = one transaction:
 * load -> apply business rule -> save -> announce the event (sent to Kafka after commit).
 */
@Service
public class CalzadoServicio {

    private final CalzadoRepository calzados;
    private final ApplicationEventPublisher eventos;

    public CalzadoServicio(CalzadoRepository calzados, ApplicationEventPublisher eventos) {
        this.calzados = calzados;
        this.eventos = eventos;
    }

    @Transactional
    public Long registrar(RegistrarCalzadoComando comando) {
        Calzado calzado = calzados.save(new Calzado(comando.marca().trim(), comando.modelo().trim(),
                comando.categoria().trim().toLowerCase(), comando.talla(), comando.color().trim().toLowerCase(),
                comando.precio(), comando.stockInicial()));
        eventos.publishEvent(EventoMapper.deCalzado(TipoEventoCalzado.CALZADO_REGISTRADO, calzado));
        return calzado.getId();
    }

    @Transactional
    public void cambiarPrecio(Long calzadoId, CambiarPrecioComando comando) {
        Calzado calzado = buscar(calzadoId);
        calzado.cambiarPrecio(comando.nuevoPrecio());
        eventos.publishEvent(EventoMapper.deCalzado(TipoEventoCalzado.PRECIO_CAMBIADO, calzado));
    }

    @Transactional
    public void reponerStock(Long calzadoId, ReponerStockComando comando) {
        Calzado calzado = buscar(calzadoId);
        calzado.reponerStock(comando.cantidad());
        eventos.publishEvent(EventoMapper.deCalzado(TipoEventoCalzado.STOCK_REPUESTO, calzado));
    }

    private Calzado buscar(Long id) {
        return calzados.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el calzado con id " + id));
    }
}
