package com.tienda.consultas.aplicacion;

import com.tienda.consultas.modelo.CalzadoVista;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/** Catalog search with optional filters, built dynamically with MongoTemplate. */
@Service
public class CatalogoConsultas {

    private final MongoTemplate mongo;

    public CatalogoConsultas(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<CalzadoVista> buscar(String marca, String categoria, Double talla, BigDecimal precioMax,
                                     boolean soloDisponibles) {
        Query consulta = new Query();
        if (StringUtils.hasText(marca)) {
            consulta.addCriteria(Criteria.where("marca").is(marca));
        }
        if (StringUtils.hasText(categoria)) {
            consulta.addCriteria(Criteria.where("categoria").is(categoria));
        }
        if (talla != null) {
            consulta.addCriteria(Criteria.where("talla").is(talla));
        }
        if (precioMax != null) {
            // Compare against Decimal128 (how the price is stored), not against a String
            consulta.addCriteria(Criteria.where("precio").lte(new Decimal128(precioMax)));
        }
        if (soloDisponibles) {
            consulta.addCriteria(Criteria.where("disponible").is(true));
        }
        consulta.with(Sort.by("marca", "modelo", "talla"));
        return mongo.find(consulta, CalzadoVista.class);
    }
}
