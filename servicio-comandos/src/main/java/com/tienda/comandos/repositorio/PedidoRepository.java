package com.tienda.comandos.repositorio;

import com.tienda.comandos.dominio.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Pedido} (table pedidos in PostgreSQL). */
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
