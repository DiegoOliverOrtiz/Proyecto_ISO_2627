package es.uclm.esibuy.backend.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.dto.CrearProductoResponse;
import es.uclm.esibuy.backend.dto.DatosProductoRequest;
import es.uclm.esibuy.backend.dto.ModificarProductoRequest;
import es.uclm.esibuy.backend.exceptions.EstadoProductoException;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
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
    private final ClienteDAO clienteDAO;

    public ProductoService(
            ProductoDAO productoDAO,
            VendedorDAO vendedorDAO,
            CategoriaDAO categoriaDAO,
            Validator validar,
            ProductoImagenService productoImagenService,
            ClienteDAO clienteDAO) {
        this.productoDAO = productoDAO;
        this.vendedorDAO = vendedorDAO;
        this.categoriaDAO = categoriaDAO;
        this.validar = validar;
        this.productoImagenService = productoImagenService;
        this.clienteDAO = clienteDAO;
    }

    public List<ConsultarProductosResponse> consultarProductosPropios(String emailVendedor) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede consultar sus productos.");

        return productoDAO.findByVendedor(vendedor.getNombreComercial()).stream()
                .map(this::aConsultarProductosResponse)
                .toList();
    }

    public List<ConsultarProductosResponse> consultarProductosParaClientes(String emailCliente) {
        clienteDAO.findByEmail(emailCliente)
                .filter(cuenta -> cuenta.isActivada() && !cuenta.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un cliente activo puede consultar los productos."));

        // Los productos desactivados no se muestran a los clientes.
        return productoDAO.findByEsVisibleTrue().stream()
                .map(this::aConsultarProductosResponse)
                .toList();
    }

    private ConsultarProductosResponse aConsultarProductosResponse(Producto producto) {
        return new ConsultarProductosResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getCategoria(),
                producto.getStock(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getDescuento(),
                producto.getDescuentoPremium(),
                producto.isEsVisible(),
                producto.getImagen());
    }

    public CrearProductoResponse crearProducto(
            String emailVendedor,
            CrearProductoRequest request,
            MultipartFile imagen) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede crear productos.");
        String nombreCategoria = validarDatosProducto(request);

        Producto producto = new Producto();
        asignarDatos(producto, request, nombreCategoria);
        producto.setEsVisible(request.esVisible() == null ? true : request.esVisible());
        producto.setVendedor(vendedor.getNombreComercial());
        String rutaImagen = productoImagenService.guardar(imagen);
        producto.setImagen(rutaImagen);

        Producto nuevoProducto = guardarEliminandoImagenSiFalla(producto, rutaImagen);
        return new CrearProductoResponse(
                nuevoProducto.getNombre(),
                nuevoProducto.getCategoria(),
                nuevoProducto.getPrecio(),
                nuevoProducto.getStock(),
                nuevoProducto.getDescuento(),
                nuevoProducto.getDescuentoPremium(),
                nuevoProducto.getImagen());
    }

    public ConsultarProductosResponse modificarProducto(
            String emailVendedor,
            String idProducto,
            ModificarProductoRequest request,
            MultipartFile imagen) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede modificar sus productos.");
        String nombreCategoria = validarDatosProducto(request);
        Producto producto = buscarProductoPropio(vendedor, idProducto);

        // Solo se actualizan los campos permitidos; el vendedor y la visibilidad se conservan.
        asignarDatos(producto, request, nombreCategoria);

        // La imagen es opcional: si no se envía una nueva, se mantiene la actual.
        String imagenAnterior = producto.getImagen();
        String imagenNueva = productoImagenService.guardar(imagen);
        if (imagenNueva != null) {
            producto.setImagen(imagenNueva);
        }

        Producto productoModificado = guardarEliminandoImagenSiFalla(producto, imagenNueva);
        if (imagenNueva != null) {
            productoImagenService.eliminar(imagenAnterior);
        }
        return aConsultarProductosResponse(productoModificado);
    }

    private Vendedor obtenerVendedorActivo(String emailVendedor, String mensajeAccesoDenegado) {
        return vendedorDAO.findByEmail(emailVendedor)
                .filter(cuenta -> cuenta.isActivada() && !cuenta.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(mensajeAccesoDenegado));
    }

    // Un producto ajeno se trata como inexistente para no revelar productos de otros vendedores.
    private Producto buscarProductoPropio(Vendedor vendedor, String idProducto) {
        return productoDAO.findById(idProducto)
                .filter(encontrado -> vendedor.getNombreComercial().equals(encontrado.getVendedor()))
                .orElseThrow(ProductoNoEncontradoException::new);
    }

    // Devuelve el nombre de la categoría normalizado si los datos son válidos.
    private String validarDatosProducto(DatosProductoRequest request) {
        if (request == null || !validar.validate(request).isEmpty()) {
            throw new InvalidProductException();
        }

        String nombreCategoria = request.categoria() == null ? "" : request.categoria().trim();
        if (nombreCategoria.isBlank() || !categoriaDAO.existsByNombreIgnoreCase(nombreCategoria)) {
            throw new InvalidProductException();
        }

        if (!sonValoresValidos(request)) {
            throw new InvalidProductException();
        }
        return nombreCategoria;
    }

    private static boolean sonValoresValidos(DatosProductoRequest request) {
        return request.nombre() != null && !request.nombre().isBlank()
                && request.precio() != null && request.precio() > 0
                && request.stock() >= 0
                && esDescuentoValido(request.descuento())
                && esDescuentoValido(request.descuentoPremium());
    }

    private static void asignarDatos(Producto producto, DatosProductoRequest request, String nombreCategoria) {
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(nombreCategoria);
        producto.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setDescuento(request.descuento());
        producto.setDescuentoPremium(request.descuentoPremium());
    }

    private static boolean esDescuentoValido(int descuento) {
        return descuento >= 0 && descuento <= 100;
    }

    private Producto guardarEliminandoImagenSiFalla(Producto producto, String rutaImagen) {
        try {
            return productoDAO.save(producto);
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
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede cambiar el estado de sus productos.");
        Producto producto = buscarProductoPropio(vendedor, idProducto);

        if (producto.isEsVisible() == activo) {
            throw new EstadoProductoException(activo);
        }

        // La desactivación es lógica: solo cambia el estado, el producto nunca se elimina.
        producto.setEsVisible(activo);
        productoDAO.save(producto);
    }
}
