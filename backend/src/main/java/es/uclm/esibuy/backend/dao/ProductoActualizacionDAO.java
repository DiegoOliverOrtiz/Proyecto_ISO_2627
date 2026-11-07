package es.uclm.esibuy.backend.dao;

import es.uclm.esibuy.backend.model.Producto;

public interface ProductoActualizacionDAO {
    Producto actualizarDatos(Producto producto, int stockAnterior);
    void actualizarVisibilidad(String id, boolean visible);
    void descontarStock(Producto producto, int cantidad);
    void reponerStock(String productoId, int cantidad);
}
