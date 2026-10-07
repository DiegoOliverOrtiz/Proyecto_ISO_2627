package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import es.uclm.esibuy.backend.model.Administrador;

public interface AdministradorDAO extends MongoRepository<Administrador, String> {

	@Query(value = "{ '_class': 'administrador' }", count = true)
	long contarAdministradores();
}