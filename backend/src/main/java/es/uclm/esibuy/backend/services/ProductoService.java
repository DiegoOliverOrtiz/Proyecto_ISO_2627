package es.uclm.esibuy.backend.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
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
    private final CategoriaDAO categoriaDAO;
    private final Validator validar;
    private final ProductoImagenService productoImagenService;

    public ProductoService(
            ProductoDAO productoDAO,
            VendedorDAO vendedorDAO,
            CategoriaDAO categoriaDAO,
            Validator validar,
            ProductoImagenService productoImagenService) {
        this.productoDAO = productoDAO;
        this.vendedorDAO = vendedorDAO;
        this.categoriaDAO = categoriaDAO;
        this.validar = validar;
        this.productoImagenService = productoImagenService;
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
                        producto.isEsVisible(),
                        producto.getImagen()))
                .toList();
    }

    public CrearProductoResponse crearProducto(
            String emailVendedor,
            CrearProductoRequest request,
            MultipartFile imagen) {
        if (request == null || !validar.validate(request).isEmpty()) {
            throw new InvalidProductException();
        }

        Vendedor vendedor = vendedorDAO.findByEmail(emailVendedor)
                .filter(cuenta -> cuenta.isActivada() && !cuenta.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un vendedor activo puede crear productos."));

        String nombreCategoria = request.categoria().trim();
        if (nombreCategoria.isBlank() || !categoriaDAO.existsByNombreIgnoreCase(nombreCategoria)) {
            throw new InvalidProductException();
        }

        double precio = request.precio();
        int stock = request.stock();
        int descuento = request.descuento();
        int descuentoPremium = request.descuentoPremium();

        if (precio <= 0 || stock < 0 || descuento < 0 || descuentoPremium < 0) {
            throw new InvalidProductException();
        }

        Producto producto = new Producto();
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(nombreCategoria);
        producto.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        producto.setPrecio(precio);
        producto.setStock(stock);
        producto.setDescuento(descuento);
        producto.setDescuentoPremium(descuentoPremium);
        producto.setEsVisible(request.esVisible() == null ? true : request.esVisible());
        producto.setVendedor(vendedor.getNombreComercial());
        String rutaImagen = productoImagenService.guardar(imagen);
        producto.setImagen(rutaImagen);

        try {
            Producto nuevoProducto = productoDAO.save(producto);
            return new CrearProductoResponse(
                    nuevoProducto.getNombre(),
                    nuevoProducto.getCategoria(),
                    nuevoProducto.getPrecio(),
                    nuevoProducto.getStock(),
                    nuevoProducto.getDescuento(),
                    nuevoProducto.getDescuentoPremium(),
                    nuevoProducto.getImagen());
        } catch (RuntimeException e) {
            if (rutaImagen != null) {
                try {
                    productoImagenService.eliminar(rutaImagen);
                } catch (RuntimeException errorLimpieza) {
                    e.addSuppressed(errorLimpieza);
                }
            }
            throw e;
        }
    }

    public void activarProducto(String emailVendedor, String idProducto) {
        cambiarEstadoProducto(emailVendedor, idProducto, true);
    }

    public void desactivarProducto(String emailVendedor, String idProducto) {
        cambiarEstadoProducto(emailVendedor, idProducto, false);
    }

    private void cambiarEstadoProducto(String emailVendedor, String idProducto, boolean activo) {
        // La desactivación es lógica: solo cambia el estado, el producto nunca se elimina.
        Producto producto = productoDAO.findById(idProducto).orElseThrow();
        producto.setEsVisible(activo);
        productoDAO.save(producto);
    }
}
