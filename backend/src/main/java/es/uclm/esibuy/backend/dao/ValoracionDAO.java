package es.uclm.esibuy.backend.dao;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Valoracion;

public interface ValoracionDAO extends MongoRepository<Valoracion, String> {
    boolean existsByClienteIdAndProductoId(String clienteId, String productoId);

    List<Valoracion> findByProductoId(String productoId);
}
