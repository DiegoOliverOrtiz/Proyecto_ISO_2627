package es.uclm.esibuy.backend.dao;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import es.uclm.esibuy.backend.model.TokenRecuperacion;

public interface TokenRecuperacionDAO
        extends MongoRepository<TokenRecuperacion, String> {

    Optional<TokenRecuperacion> findByHashToken(String hashToken);

    long deleteByUsuarioId(String usuarioId);

    @Query("{ '_id': ?0, 'consumido': false, 'caducidad': { '$gt': ?1 } }")
    @Update("{ '$set': { 'consumido': true } }")
    long consumirSiValido(String id, Instant ahora);
}