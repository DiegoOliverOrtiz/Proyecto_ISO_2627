package es.uclm.esibuy.backend.dao;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import es.uclm.esibuy.backend.model.Usuario;

public interface UsuarioDAO extends Repository<Usuario, String> {
	boolean existsByEmail(String email);

	Optional<Usuario> findByEmail(String email);
}