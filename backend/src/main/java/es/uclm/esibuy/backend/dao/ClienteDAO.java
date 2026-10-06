package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Cliente;

public interface ClienteDAO extends MongoRepository<Cliente, String> {
}
