package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Carrito;

public interface CarritoDAO extends MongoRepository<Carrito, String> {
}
