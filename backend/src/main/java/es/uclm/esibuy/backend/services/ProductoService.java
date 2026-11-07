package es.uclm.esibuy.backend.services;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.dto.CrearProductoResponse;
import es.uclm.esibuy.backend.dto.DatosProductoRequest;
import es.uclm.esibuy.backend.dto.FiltroProductosRequest;
import es.uclm.esibuy.backend.dto.ModificarProductoRequest;
import es.uclm.esibuy.backend.dto.ProductoAdministracionResponse;
import es.uclm.esibuy.backend.dto.ProductoCatalogoResponse;
import es.uclm.esibuy.backend.exceptions.EstadoProductoException;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@Service
public class ProductoService {
    // Listado del administrador: por vendedor y después por nombre, sin distinguir mayúsculas.
    private static final Comparator<Producto> ORDEN_ADMINISTRACION = Comparator
            .comparing(Producto::getVendedor, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Producto::getNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final ProductoDAO productoDAO;
    private final VendedorDAO vendedorDAO;
    private final CategoriaDAO categoriaDAO;
    private final Validator validar;
    private final ProductoImagenService productoImagenService;
    private final ClienteDAO clienteDAO;
    private final PedidoDAO pedidoDAO;

    public ProductoService(
            ProductoDAO productoDAO,
            VendedorDAO vendedorDAO,
            CategoriaDAO categoriaDAO,
            Validator validar,
            ProductoImagenService productoImagenService,
            ClienteDAO clienteDAO,
            PedidoDAO pedidoDAO) {
        this.productoDAO = productoDAO;
        this.vendedorDAO = vendedorDAO;
        this.categoriaDAO = categoriaDAO;
        this.validar = validar;
        this.productoImagenService = productoImagenService;
        this.clienteDAO = clienteDAO;
        this.pedidoDAO = pedidoDAO;
    }

    public List<ConsultarProductosResponse> consultarProductosPropios(String emailVendedor) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede consultar sus productos.");

        return productoDAO.findByVendedor(vendedor.getNombreComercial()).stream()
                .map(this::aConsultarProductosResponse)
                .toList();
    }

    // HU-34: el administrador ve todos los productos (también los desactivados) en solo lectura.
    public List<ProductoAdministracionResponse> consultarProductosParaAdministrador() {
        return productoDAO.findAll().stream()
                .sorted(ORDEN_ADMINISTRACION)
                .map(this::aProductoAdministracionResponse)
                .toList();
    }

    public List<ProductoCatalogoResponse> consultarProductosParaClientes(
            String emailCliente,
            FiltroProductosRequest filtros) {
        Cliente cliente = obtenerClienteActivo(emailCliente);
        validarFiltros(filtros);

        String nombre = normalizarBusqueda(filtros.nombre() == null ? "" : filtros.nombre().trim());
        return productoDAO.findByEsVisibleTrue().stream()
                .filter(producto -> nombre.isEmpty()
                        || normalizarBusqueda(producto.getNombre()).contains(nombre))
                .filter(producto -> filtros.categoria() == null || filtros.categoria().isBlank()
                        || producto.getCategoria().equalsIgnoreCase(filtros.categoria().trim()))
                .map(producto -> aProductoCatalogoResponse(producto, cliente.isEsPremium()))
                .filter(producto -> filtros.precioMinimo() == null
                        || (producto.precio() != null && producto.precio() >= filtros.precioMinimo()))
                .filter(producto -> filtros.precioMaximo() == null
                        || (producto.precio() != null && producto.precio() <= filtros.precioMaximo()))
                .filter(producto -> filtros.disponible() == null
                        || (Boolean.TRUE.equals(filtros.disponible()) ? producto.stock() > 0 : producto.stock() <= 0))
                .toList();
    }

    public ProductoCatalogoResponse consultarDetalleParaCliente(String emailCliente, String idProducto) {
        Cliente cliente = obtenerClienteActivo(emailCliente);
        Producto producto = productoDAO.findById(idProducto)
                .filter(Producto::isEsVisible)
                .orElseThrow(ProductoNoEncontradoException::new);

        if (producto.isSoloPremium() && !cliente.isEsPremium()) {
            throw new AccessDeniedException("Este producto solo está disponible para clientes Premium.");
        }

        return aProductoCatalogoResponse(producto, cliente.isEsPremium());
    }

    private Cliente obtenerClienteActivo(String emailCliente) {
        return clienteDAO.findByEmail(emailCliente)
                .filter(cuenta -> cuenta.isActivada() && !cuenta.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un cliente activo puede consultar los productos."));
    }

    private static void validarFiltros(FiltroProductosRequest filtros) {
        if (filtros == null) {
            throw new IllegalArgumentException("Los filtros de productos son obligatorios.");
        }
        boolean minimoInvalido = filtros.precioMinimo() != null
                && (!Double.isFinite(filtros.precioMinimo()) || filtros.precioMinimo() < 0);
        boolean maximoInvalido = filtros.precioMaximo() != null
                && (!Double.isFinite(filtros.precioMaximo()) || filtros.precioMaximo() < 0);
        if (minimoInvalido || maximoInvalido) {
            throw new IllegalArgumentException("Los filtros de precio deben ser valores positivos válidos.");
        }
        if (filtros.precioMinimo() != null
                && filtros.precioMaximo() != null
                && filtros.precioMinimo() > filtros.precioMaximo()) {
            throw new IllegalArgumentException("El precio mínimo no puede superar el precio máximo.");
        }
    }

    private ProductoCatalogoResponse aProductoCatalogoResponse(Producto producto, boolean clientePremium) {
        int descuentoAplicado = PrecioProducto.descuentoAplicable(producto, clientePremium);
        return new ProductoCatalogoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getCategoria(),
                producto.getStock(),
                PrecioProducto.paraCliente(producto, clientePremium),
                producto.getPrecio(),
                producto.getDescuento(),
                producto.getDescuentoPremium(),
                descuentoAplicado,
                !producto.isSoloPremium() || clientePremium ? producto.getDescripcion() : null,
                producto.getImagen(),
                producto.getVendedor(),
                producto.isEsVisible(),
                producto.isSoloPremium(),
                !producto.isSoloPremium() || clientePremium);
    }

    private ConsultarProductosResponse aConsultarProductosResponse(Producto producto) {
        return aConsultarProductosResponse(producto, true);
    }

    private ConsultarProductosResponse aConsultarProductosResponse(Producto producto, boolean incluirDescripcion) {
        return new ConsultarProductosResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getCategoria(),
                producto.getStock(),
                incluirDescripcion ? producto.getDescripcion() : null,
                producto.getPrecio(),
                producto.getDescuento(),
                producto.getDescuentoPremium(),
                producto.isEsVisible(),
                producto.getImagen(),
                producto.isSoloPremium());
    }

    private ProductoAdministracionResponse aProductoAdministracionResponse(Producto producto) {
        return new ProductoAdministracionResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getCategoria(),
                producto.getStock(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getDescuento(),
                producto.getDescuentoPremium(),
                producto.isEsVisible(),
                producto.getImagen(),
                producto.isSoloPremium(),
                producto.getVendedor());
    }

    public CrearProductoResponse crearProducto(
            String emailVendedor,
            CrearProductoRequest request,
            MultipartFile imagen) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede crear productos.");
        String nombreCategoria = validarDatosProducto(request);
        // Al crear el stock inicial debe ser mayor que 0 (al modificar se admite 0).
        if (request.stock() <= 0) {
            throw new InvalidProductException();
        }

        Producto producto = new Producto();
        asignarDatos(producto, request, nombreCategoria);
        producto.setEsVisible(request.esVisible() == null || request.esVisible());
        producto.setSoloPremium(Boolean.TRUE.equals(request.soloPremium()));
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

        int stockAnterior = producto.getStock();

        // Solo se actualizan los campos permitidos; el vendedor y la visibilidad se conservan.
        asignarDatos(producto, request, nombreCategoria);
        // La marca «solo premium» es opcional: si no se envía, se conserva la actual.
        if (request.soloPremium() != null) {
            producto.setSoloPremium(request.soloPremium());
        }

        // La imagen es opcional: si no se envía una nueva, se mantiene la actual.
        String imagenAnterior = producto.getImagen();
        String imagenNueva = productoImagenService.guardar(imagen);
        if (imagenNueva != null) {
            producto.setImagen(imagenNueva);
        }

        Producto productoModificado;
        try {
            productoModificado = productoDAO.actualizarDatos(producto, stockAnterior);
        } catch (RuntimeException exception) {
            if (imagenNueva != null) {
                try {
                    productoImagenService.eliminar(imagenNueva);
                } catch (RuntimeException limpieza) {
                    exception.addSuppressed(limpieza);
                }
            }
            throw exception;
        }
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
        productoDAO.actualizarVisibilidad(producto.getId(), activo);
    }

    public void eliminarProducto(String emailVendedor, String idProducto) {
        Vendedor vendedor = obtenerVendedorActivo(
                emailVendedor, "Solo un vendedor activo puede eliminar sus productos.");
        Producto producto = productoDAO.findById(idProducto)
                .orElseThrow(ProductoNoEncontradoException::new);
        EliminacionProducto.comprobarEliminable(vendedor, producto, pedidoDAO);

        productoDAO.delete(producto);
        EliminacionProducto.eliminarImagen(productoImagenService, producto.getImagen());
    }

    /*
     * M22: la búsqueda no distingue mayúsculas ni tildes («raton» encuentra «Ratón»).
     * Se compara siempre como texto literal (contains), nunca como expresión regular.
     */
    static String normalizarBusqueda(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
