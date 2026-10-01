package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Vendedor;

public interface VendedorDAO extends MongoRepository<Vendedor, String> {
	boolean existsByNombreComercial(String nombreComercial);
}