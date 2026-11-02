package es.uclm.esibuy.backend.dao;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.ListaProductos;

public interface ListaProductosDAO
        extends MongoRepository<ListaProductos, String> {

    List<ListaProductos> findByUsuarioId(String usuarioId);

    void deleteByUsuarioId(String usuarioId);
}
