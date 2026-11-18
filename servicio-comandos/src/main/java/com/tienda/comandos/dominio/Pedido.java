package com.tienda.comandos.dominio;

import com.tienda.eventos.EstadoPedido;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Write model of an order, stored in PostgreSQL with a real foreign key to the shoe
 * (referential integrity is one of the reasons the write side uses a relational database).
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "calzado_id")
    private Calzado calzado;

    @Column(nullable = false)
    private int cantidad;

    /** Price at the moment of buying: a later price change must not alter this order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String clienteNombre;

    @Column(nullable = false)
    private String clienteEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @Column(nullable = false)
    private Instant creadoEn;

    @Version
    private Long version;

    protected Pedido() {
        // required by JPA
    }

    /**
     * Creates an order and takes the units out of the stock.
     * If there is not enough stock, {@link Calzado#descontarStock} throws and no order is created.
     */
    public static Pedido crear(Calzado calzado, int cantidad, String clienteNombre, String clienteEmail) {
        calzado.descontarStock(cantidad);

        Pedido pedido = new Pedido();
        pedido.calzado = calzado;
        pedido.cantidad = cantidad;
        pedido.precioUnitario = calzado.getPrecio();
        pedido.total = calzado.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        pedido.clienteNombre = clienteNombre;
        pedido.clienteEmail = clienteEmail;
        pedido.estado = EstadoPedido.CREADO;
        pedido.creadoEn = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return pedido;
    }

    public void despachar() {
        exigirEstadoCreado("despachar");
        this.estado = EstadoPedido.DESPACHADO;
    }

    /** Cancelling returns the units to the stock. */
    public void cancelar() {
        exigirEstadoCreado("cancelar");
        this.estado = EstadoPedido.CANCELADO;
        calzado.devolverStock(cantidad);
    }

    public boolean perteneceA(String email) {
        return clienteEmail.equalsIgnoreCase(email);
    }

    private void exigirEstadoCreado(String accion) {
        if (estado != EstadoPedido.CREADO) {
            throw new ReglaNegocioException("No se puede " + accion + " el pedido #" + id
                    + " porque su estado es " + estado + " (solo se permite en estado CREADO)");
        }
    }

    public Long getId() {
        return id;
    }

    public Calzado getCalzado() {
        return calzado;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
