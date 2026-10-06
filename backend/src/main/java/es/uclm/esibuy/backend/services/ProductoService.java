package es.uclm.esibuy.backend.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.dto.CrearProductoResponse;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@Service
public class ProductoService {
    private final ProductoDAO productoDAO;
    private final VendedorDAO vendedorDAO;
    private final Validator validar;

    public ProductoService(ProductoDAO productoDAO, VendedorDAO vendedorDAO, Validator validar) {
        this.productoDAO = productoDAO;
        this.vendedorDAO = vendedorDAO;
        this.validar = validar;
    }

    public List<ConsultarProductosResponse> consultarProductosPropios(String emailVendedor) {
        Vendedor vendedor = vendedorDAO.findByEmail(emailVendedor)
                .filter(cuenta -> cuenta.isActivada() && !cuenta.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un vendedor activo puede consultar sus productos."));

        return productoDAO.findByVendedor(vendedor.getNombreComercial()).stream()
                .map(producto -> new ConsultarProductosResponse(
                        producto.getNombre(),
                        producto.getCategoria(),
                        producto.getStock(),
                        producto.getDescripcion(),
                        producto.getPrecio(),
                        producto.getDescuento(),
                        producto.getDescuentoPremium(),
                        producto.isEsVisible()))
                .toList();
    }

    public CrearProductoResponse crearProducto(CrearProductoRequest request) {
        if (request == null || !validar.validate(request).isEmpty()) {
            throw new InvalidProductException();
        }
        Producto producto = new Producto();
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(request.categoria().trim());
        producto.setDescripcion(request.descripcion().trim());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setVendedor(request.vendedor());

        try {
            Producto nuevoProducto = productoDAO.save(producto);
            return new CrearProductoResponse(nuevoProducto.getNombre(), nuevoProducto.getCategoria(), nuevoProducto.getPrecio(), nuevoProducto.getStock(), nuevoProducto.getDescuento(), nuevoProducto.getDescuentoPremium());
        } catch (Exception e) {
            throw new InvalidProductException();
        }
    }
}
