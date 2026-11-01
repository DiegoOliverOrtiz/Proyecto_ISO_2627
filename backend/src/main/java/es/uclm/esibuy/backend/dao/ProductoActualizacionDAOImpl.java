package es.uclm.esibuy.backend.dao;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import es.uclm.esibuy.backend.model.Producto;

public class ProductoActualizacionDAOImpl implements ProductoActualizacionDAO {
    private final MongoTemplate mongoTemplate;

    public ProductoActualizacionDAOImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Producto actualizarDatos(Producto producto, int stockAnterior) {
        Query query = Query.query(Criteria.where("id").is(producto.getId()).and("stock").is(stockAnterior));
        Update update = new Update().set("nombre", producto.getNombre()).set("categoria", producto.getCategoria())
                .set("descripcion", producto.getDescripcion()).set("precio", producto.getPrecio())
                .set("stock", producto.getStock()).set("descuento", producto.getDescuento())
                .set("descuentoPremium", producto.getDescuentoPremium()).set("soloPremium", producto.isSoloPremium())
                .set("imagen", producto.getImagen());
        comprobarActualizacion(mongoTemplate.updateFirst(query, update, Producto.class).getMatchedCount());
        return producto;
    }

    @Override
    public void actualizarVisibilidad(String id, boolean visible) {
        comprobarActualizacion(mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(id)),
                new Update().set("esVisible", visible), Producto.class).getMatchedCount());
    }

    @Override
    public void descontarStock(Producto producto, int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        Query query = Query.query(Criteria.where("id").is(producto.getId())
                .and("stock").gte(cantidad).and("esVisible").is(true)
                .and("precio").is(producto.getPrecio()).and("descuento").is(producto.getDescuento())
                .and("descuentoPremium").is(producto.getDescuentoPremium())
                .and("soloPremium").is(producto.isSoloPremium()));
        long modificados = mongoTemplate.updateFirst(query, new Update().inc("stock", -cantidad), Producto.class)
                .getModifiedCount();
        if (modificados != 1) {
            throw new IllegalArgumentException("El producto ha cambiado o no hay stock suficiente.");
        }
    }

    private void comprobarActualizacion(long encontrados) {
        if (encontrados != 1) {
            throw new OptimisticLockingFailureException("El producto ha cambiado. Actualiza e inténtalo de nuevo.");
        }
    }
}
