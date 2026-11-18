package com.tienda.consultas.repositorio;

import com.tienda.consultas.modelo.CalzadoVista;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Data access for the "catalogo" collection in MongoDB. */
public interface CalzadoVistaRepository extends MongoRepository<CalzadoVista, Long> {
}
