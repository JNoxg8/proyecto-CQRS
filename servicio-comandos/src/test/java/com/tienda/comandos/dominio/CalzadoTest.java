package com.tienda.comandos.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests of the business rules: no Spring, no database, no Kafka. They run in milliseconds. */
class CalzadoTest {

    private Calzado nuevoCalzado(int stock) {
        return new Calzado("Nike", "Air Max 90", "deportivo", 40, "blanco", new BigDecimal("459900"), stock);
    }

    @Test
    void descontarStockRestaLasUnidades() {
        Calzado calzado = nuevoCalzado(5);

        calzado.descontarStock(2);

        assertThat(calzado.getStock()).isEqualTo(3);
    }

    @Test
    void noPermiteVenderMasDeLoQueHay() {
        Calzado calzado = nuevoCalzado(1);

        assertThatThrownBy(() -> calzado.descontarStock(2))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(calzado.getStock()).isEqualTo(1);   // nothing changed
    }

    @Test
    void permiteVenderExactamenteElUltimoPar() {
        Calzado calzado = nuevoCalzado(1);

        calzado.descontarStock(1);

        assertThat(calzado.getStock()).isZero();
    }

    @Test
    void rechazaCantidadesNoPositivas() {
        Calzado calzado = nuevoCalzado(5);

        assertThatThrownBy(() -> calzado.descontarStock(0)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> calzado.reponerStock(-3)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaPrecioCeroONegativo() {
        Calzado calzado = nuevoCalzado(5);

        assertThatThrownBy(() -> calzado.cambiarPrecio(BigDecimal.ZERO)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> calzado.cambiarPrecio(new BigDecimal("-1"))).isInstanceOf(ReglaNegocioException.class);
        assertThat(calzado.getPrecio()).isEqualByComparingTo("459900");
    }

    @Test
    void rechazaStockInicialNegativo() {
        assertThatThrownBy(() -> nuevoCalzado(-1)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void describeTallasEnterasSinDecimales() {
        assertThat(nuevoCalzado(1).descripcion()).isEqualTo("Nike Air Max 90 - talla 40 - blanco");
    }
}
