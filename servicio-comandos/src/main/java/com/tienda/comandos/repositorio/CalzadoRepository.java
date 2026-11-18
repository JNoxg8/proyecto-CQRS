package com.tienda.comandos.repositorio;

import com.tienda.comandos.dominio.Calzado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalzadoRepository extends JpaRepository<Calzado, Long> {
}
