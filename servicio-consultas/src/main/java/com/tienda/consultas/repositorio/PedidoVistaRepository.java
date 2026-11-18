package com.tienda.consultas.repositorio;

import com.tienda.consultas.modelo.PedidoVista;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Data access for the "pedidos" collection in MongoDB. */
public interface PedidoVistaRepository extends MongoRepository<PedidoVista, Long> {

    /** Spring Data builds the Mongo query from the method name: {clienteEmail: ?} sorted by creadoEn desc. */
    List<PedidoVista> findByClienteEmailOrderByCreadoEnDesc(String clienteEmail);
}
