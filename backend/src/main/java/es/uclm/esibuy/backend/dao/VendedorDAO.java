package es.uclm.esibuy.backend.dao;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Vendedor;

public interface VendedorDAO extends MongoRepository<Vendedor, String> {
    Optional<Vendedor> findByNombreComercial(String nombreComercial);
	boolean existsByNombreComercial(String nombreComercial);

	Optional<Vendedor> findByEmail(String email);
}
