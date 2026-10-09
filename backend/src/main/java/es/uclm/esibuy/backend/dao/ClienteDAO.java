package es.uclm.esibuy.backend.dao;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Cliente;

public interface ClienteDAO extends MongoRepository<Cliente, String> {
	Optional<Cliente> findByEmail(String email);
}
