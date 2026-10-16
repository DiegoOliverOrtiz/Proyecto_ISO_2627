package es.uclm.esibuy.backend.dao;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Producto;

public interface ProductoDAO extends MongoRepository<Producto, String> {
    List<Producto> findByVendedor(String nombreComercial);

    List<Producto> findByEsVisibleTrue();

    boolean existsByCategoriaIgnoreCase(String categoria);

    List<Producto> findByCategoriaIgnoreCase(String categoria);
}
