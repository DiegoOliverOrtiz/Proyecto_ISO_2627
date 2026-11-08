package es.uclm.esibuy.backend.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import es.uclm.esibuy.backend.model.CambioEstadoPedido;
import es.uclm.esibuy.backend.model.Pedido;

public interface PedidoDAO extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    @Query("{ 'lineas.vendedorId': ?0 }")
    List<Pedido> findByVendedorId(String vendedorId);

    Optional<Pedido> findByIdAndClienteId(String id, String clienteId);

    // Cambio atómico: solo se aplica si el pedido sigue en el estado esperado y contiene productos del vendedor.
    @Query("{ '_id': ?0, 'estado': ?1, 'lineas.vendedorId': ?2 }")
    @Update("{ '$set': { 'estado': ?3, 'historialEstados': ?4 } }")
    long actualizarEstado(
            String pedidoId,
            String estadoActual,
            String vendedorId,
            String estadoNuevo,
            List<CambioEstadoPedido> historialEstados);

    @Query("{ '_id': ?0, 'clienteId': ?1, 'estado': ?2 }")
    @Update("{ '$set': { 'estado': ?3, 'historialEstados': ?4 } }")
    long cancelarPedido(
            String pedidoId,
            String clienteId,
            String estadoActual,
            String estadoNuevo,
            List<CambioEstadoPedido> historialEstados);

    @Query("{ '_id': ?0, 'clienteId': ?1, 'estado': ?2 }")
    @Update("{ '$set': { 'estado': ?3, 'historialEstados': ?4 } }")
    long finalizarPedido(
            String pedidoId,
            String clienteId,
            String estadoActual,
            String estadoNuevo,
            List<CambioEstadoPedido> historialEstados);

    // Indica si el producto figura en algún pedido con uno de los estados indicados (por nombre).
    @Query(value = "{ 'lineas.productoId': ?0, 'estado': { '$in': ?1 } }", exists = true)
    boolean existsByProductoIdAndEstadoIn(String productoId, List<String> estados);
}

