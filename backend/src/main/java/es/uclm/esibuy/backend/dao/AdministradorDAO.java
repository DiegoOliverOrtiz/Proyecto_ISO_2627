package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Administrador;

public interface AdministradorDAO extends MongoRepository<Administrador, String> {
}