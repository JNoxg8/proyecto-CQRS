package com.tienda.comandos.repositorio;

import com.tienda.comandos.dominio.Calzado;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Calzado} (table calzados in PostgreSQL). */
public interface CalzadoRepository extends JpaRepository<Calzado, Long> {
}
