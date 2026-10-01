package es.uclm.esibuy.backend.services;

import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.dto.CrearProductoResponse;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.model.Producto;
import jakarta.validation.Validator;

public class ProductoService {
    private final ProductoDAO productoDAO;
    private final Validator validar;

    public ProductoService (ProductoDAO productoDAO, Validator validar){
        this.productoDAO = productoDAO;
        this.validar = validar;
    }

    public CrearProductoResponse crearProducto(CrearProductoRequest request){
        if(request == null || !validar.validate(request).isEmpty()){
            throw new InvalidProductException();
        }
        Producto producto = new Producto();
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(request.categoria().trim());
        producto.setDescripcion(request.descripcion().trim());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock() - 1);

        try{
            Producto nuevoProducto = productoDAO.save(producto);
            return new CrearProductoResponse(nuevoProducto.getNombre(), nuevoProducto.getCategoria(), nuevoProducto.getPrecio(), nuevoProducto.getStock(), nuevoProducto.getDescuento(), nuevoProducto.getDescuentoPremium());
        }catch (Exception e){
            throw new InvalidProductException();
        }
    }
}
