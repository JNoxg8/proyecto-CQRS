package com.tienda.comandos.dominio;

import com.tienda.eventos.EstadoPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTest {

    private Calzado calzadoConStock(int stock) {
        return new Calzado("Adidas", "Samba OG", "casual", 39, "negro", new BigDecimal("389900.50"), stock);
    }

    @Test
    void crearPedidoDescuentaStockYCalculaElTotal() {
        Calzado calzado = calzadoConStock(5);

        Pedido pedido = Pedido.crear(calzado, 2, "Ana", "ana@correo.com");

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("779801.00");
        assertThat(calzado.getStock()).isEqualTo(3);
    }

    @Test
    void sinStockNoSeCreaElPedido() {
        Calzado calzado = calzadoConStock(1);

        assertThatThrownBy(() -> Pedido.crear(calzado, 3, "Ana", "ana@correo.com"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(calzado.getStock()).isEqualTo(1);
    }

    @Test
    void elPedidoConservaElPrecioDelMomentoDeLaCompra() {
        Calzado calzado = calzadoConStock(5);
        Pedido pedido = Pedido.crear(calzado, 1, "Ana", "ana@correo.com");

        calzado.cambiarPrecio(new BigDecimal("999999"));

        assertThat(pedido.getPrecioUnitario()).isEqualByComparingTo("389900.50");
    }

    @Test
    void cancelarDevuelveLasUnidadesAlStock() {
        Calzado calzado = calzadoConStock(5);
        Pedido pedido = Pedido.crear(calzado, 2, "Ana", "ana@correo.com");

        pedido.cancelar();

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(calzado.getStock()).isEqualTo(5);
    }

    @Test
    void unPedidoDespachadoNoSePuedeCancelar() {
        Calzado calzado = calzadoConStock(5);
        Pedido pedido = Pedido.crear(calzado, 2, "Ana", "ana@correo.com");
        pedido.despachar();

        assertThatThrownBy(pedido::cancelar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("DESPACHADO");
        assertThat(calzado.getStock()).isEqualTo(3);   // the units were NOT returned
    }

    @Test
    void elPedidoSabeQuienEsSuDueno() {
        Pedido pedido = Pedido.crear(calzadoConStock(5), 1, "Ana", "ana@correo.com");

        assertThat(pedido.perteneceA("ANA@correo.com")).isTrue();
        assertThat(pedido.perteneceA("luis@correo.com")).isFalse();
    }

    @Test
    void unPedidoCanceladoNoSePuedeDespacharNiCancelarDeNuevo() {
        Pedido pedido = Pedido.crear(calzadoConStock(5), 1, "Ana", "ana@correo.com");
        pedido.cancelar();

        assertThatThrownBy(pedido::despachar).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(pedido::cancelar).isInstanceOf(ReglaNegocioException.class);
    }
}
