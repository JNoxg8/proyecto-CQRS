package com.tienda.comandos.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Write model of a shoe (one reference in one size), stored in PostgreSQL.
 *
 * <p>Business rules live <b>inside</b> the entity (rich domain model): nobody can leave the stock
 * negative or set a price of zero, no matter which service method calls it.
 */
@Entity
@Table(name = "calzados")
public class Calzado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private double talla;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;   // money: exact decimal, never double

    @Column(nullable = false)
    private int stock;

    /**
     * Optimistic locking: if two orders modify the same shoe at the same time, the second commit
     * fails instead of silently overwriting the stock ("lost update").
     */
    @Version
    private Long version;

    protected Calzado() {
        // required by JPA
    }

    /**
     * Creates a shoe.
     * @throws ReglaNegocioException if the initial stock is negative or the price is not positive
     */
    public Calzado(String marca, String modelo, String categoria, double talla,
                   String color, BigDecimal precio, int stockInicial) {
        if (stockInicial < 0) {
            throw new ReglaNegocioException("El stock inicial no puede ser negativo");
        }
        this.marca = marca;
        this.modelo = modelo;
        this.categoria = categoria;
        this.talla = talla;
        this.color = color;
        this.stock = stockInicial;
        cambiarPrecio(precio);
    }

    // ---------- business rules ----------

    /** Rule: the price must be greater than zero. */
    public void cambiarPrecio(BigDecimal nuevoPrecio) {
        if (nuevoPrecio == null || nuevoPrecio.signum() <= 0) {
            throw new ReglaNegocioException("El precio debe ser mayor que cero");
        }
        this.precio = nuevoPrecio;
    }

    /** Rule: only positive quantities can be added. */
    public void reponerStock(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad a reponer debe ser mayor que cero");
        }
        this.stock += cantidad;
    }

    /**
     * Rule: the stock can never become negative (no overselling).
     * @throws ReglaNegocioException if there are fewer units than requested
     */
    public void descontarStock(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor que cero");
        }
        if (cantidad > stock) {
            throw new ReglaNegocioException("Stock insuficiente para " + descripcion()
                    + ": pediste " + cantidad + " y solo hay " + stock + " disponible(s)");
        }
        this.stock -= cantidad;
    }

    /** Gives back the units of a cancelled order. */
    public void devolverStock(int cantidad) {
        this.stock += cantidad;
    }

    /** Human readable text, e.g. "Nike Air Max 90 - talla 40.5 - blanco". */
    public String descripcion() {
        String tallaTexto = talla == Math.floor(talla) ? String.valueOf((int) talla) : String.valueOf(talla);
        return marca + " " + modelo + " - talla " + tallaTexto + " - " + color;
    }

    // ---------- getters (no setters: state only changes through the business methods) ----------

    public Long getId() {
        return id;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public String getCategoria() {
        return categoria;
    }

    public double getTalla() {
        return talla;
    }

    public String getColor() {
        return color;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public int getStock() {
        return stock;
    }
}
