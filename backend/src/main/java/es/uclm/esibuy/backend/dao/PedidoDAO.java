package es.uclm.esibuy.backend.dao;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.backend.model.Pedido;

public interface PedidoDAO extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    List<Pedido> findByLineasVendedorId(String vendedorId);
}
