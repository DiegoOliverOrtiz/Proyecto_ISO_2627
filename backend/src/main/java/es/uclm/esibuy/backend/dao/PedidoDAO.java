package es.uclm.esibuy.backend.dao;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import es.uclm.esibuy.backend.model.Pedido;

public interface PedidoDAO extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    @Query("{ 'lineas.vendedorId': ?0 }")
    List<Pedido> findByVendedorId(String vendedorId);
}
