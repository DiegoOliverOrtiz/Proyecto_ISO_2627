package es.uclm.esibuy.backend.dao;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Usuario;

public interface UsuarioDAO extends MongoRepository<Usuario, String>, UsuarioConsultaDAO {
	boolean existsByEmail(String email);

	Optional<Usuario> findByEmail(String email);
}