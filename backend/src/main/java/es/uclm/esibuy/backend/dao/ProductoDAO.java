package es.uclm.esibuy.backend.dao;
import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Producto;

public interface ProductoDAO extends MongoRepository<Producto, String> {
    
}
