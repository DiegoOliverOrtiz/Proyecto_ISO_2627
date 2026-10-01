package es.uclm.esibuy.backend.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Categoria;

public interface CategoriaDAO extends MongoRepository<Categoria, String> {
}