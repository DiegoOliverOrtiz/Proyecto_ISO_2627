package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dto.CarritoResponse;
import es.uclm.esibuy.backend.dto.LineaCarritoResponse;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Carrito;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;

@Service
public class CarritoService {
    private static final String MENSAJE_CARRITO_NO_ACTUALIZADO = "No se ha podido actualizar el carrito.";
    private static final String MENSAJE_SOLO_PREMIUM = "Este producto solo está disponible para clientes Premium.";
    private static final String MENSAJE_PRECIO_NO_DISPONIBLE = "Un producto del carrito no tiene un precio disponible.";
    private static final int MAXIMO_INTENTOS = 3;
    private final CarritoDAO carritoDAO;
    private final ClienteDAO clienteDAO;
    private final ProductoDAO productoDAO;

    public CarritoService(CarritoDAO carritoDAO, ClienteDAO clienteDAO, ProductoDAO productoDAO) {
        this.carritoDAO = carritoDAO;
        this.clienteDAO = clienteDAO;
        this.productoDAO = productoDAO;
    }

    public Carrito anadirProducto(String idProducto, int cantidad) {
        return actualizarCarrito(idProducto, cantidad).carrito();
    }

    public CarritoResponse anadirProductoConImporte(String idProducto, int cantidad) {
        return actualizarCarrito(idProducto, cantidad).respuesta();
    }

    public CarritoResponse consultarCarrito() {
        Cliente cliente = obtenerClienteAutenticado();
        return presentarCarrito(carritoDAO.findById(cliente.getId())
                .orElseGet(() -> new Carrito(cliente.getId())), cliente);
    }

    public CarritoResponse modificarCantidad(String idProducto, int cantidad) {
        return modificarLinea(idProducto, cantidad);
    }

    public CarritoResponse eliminarProducto(String idProducto) {
        return modificarLinea(idProducto, null);
    }

    public CarritoResponse descartarCarrito() {
        Cliente cliente = obtenerClienteAutenticado();
        return reintentarSiHayConflicto(() -> {
            Carrito carrito = carritoDAO.findById(cliente.getId()).orElse(null);
            if (carrito == null) {
                return presentarCarrito(new Carrito(cliente.getId()), cliente);
            }
            Carrito vacio = carrito.vacio();
            carritoDAO.save(vacio);
            return presentarCarrito(vacio, cliente);
        });
    }

    /*
     * Repite la operación si otra petición ha modificado el mismo carrito a la vez (@Version)
     * o si dos primeras altas simultáneas chocan; en el último intento relanza la excepción.
     */
    private <T> T reintentarSiHayConflicto(Supplier<T> operacion) {
        for (int intento = 1; intento <= MAXIMO_INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                if (intento == MAXIMO_INTENTOS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException(MENSAJE_CARRITO_NO_ACTUALIZADO);
    }

    private CarritoResponse modificarLinea(String idProducto, Integer cantidad) {
        Cliente cliente = obtenerClienteAutenticado();
        if (idProducto == null || idProducto.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }
        if (cantidad != null && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        return reintentarSiHayConflicto(() -> {
            Carrito carrito = carritoDAO.findById(cliente.getId())
                    .orElseThrow(ProductoNoEncontradoException::new);
            if (!carrito.getProductos().containsKey(idProducto)) {
                throw new ProductoNoEncontradoException();
            }
            Producto productoModificado = cantidad == null ? null
                    : obtenerProductoModificable(idProducto, cantidad, cliente);
            Carrito actualizado = cantidad == null ? carrito.sinProducto(idProducto)
                    : carrito.conCantidad(idProducto, cantidad);
            CarritoResponse respuesta = presentarCarrito(actualizado, cliente, productoModificado);
            carritoDAO.save(actualizado);
            return respuesta;
        });
    }

    private Producto obtenerProductoModificable(String idProducto, int cantidad, Cliente cliente) {
        Producto producto = productoDAO.findById(idProducto)
                .orElseThrow(ProductoNoEncontradoException::new);
        if (producto.isSoloPremium() && !cliente.isEsPremium()) {
            throw new AccessDeniedException(MENSAJE_SOLO_PREMIUM);
        }
        String aviso = comprobarDisponibilidad(producto, cantidad, cliente);
        if (aviso != null) {
            throw new IllegalArgumentException(aviso);
        }
        return producto;
    }

    private CarritoResponse presentarCarrito(Carrito carrito, Cliente cliente) {
        return presentarCarrito(carrito, cliente, null);
    }

    private CarritoResponse presentarCarrito(Carrito carrito, Cliente cliente, Producto productoModificado) {
        List<LineaCarritoResponse> lineas = new ArrayList<>();
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        for (var entrada : carrito.getProductos().entrySet()) {
            Producto producto = productoModificado != null && entrada.getKey().equals(productoModificado.getId())
                    ? productoModificado : productoDAO.findById(entrada.getKey()).orElse(null);
            String aviso = comprobarDisponibilidad(producto, entrada.getValue(), cliente);
            if (aviso == null) {
                LineaCarritoResponse linea = calcularLinea(producto, entrada.getValue(), cliente);
                importe = importe.add(linea.subtotal());
                lineas.add(linea);
            } else {
                lineas.add(lineaNoDisponible(entrada.getKey(), entrada.getValue(), producto, aviso));
            }
        }
        return new CarritoResponse(List.copyOf(lineas), importe);
    }

    private static LineaCarritoResponse lineaNoDisponible(String idProducto, int cantidad, Producto producto,
            String aviso) {
        if (producto == null) {
            return new LineaCarritoResponse(idProducto, cantidad, null, null, "Producto eliminado", null, null,
                    false, aviso, null);
        }
        return new LineaCarritoResponse(idProducto, cantidad, null, null, producto.getNombre(),
                producto.getImagen(), producto.getStock(), false, aviso, producto.getVendedor());
    }

    private String comprobarDisponibilidad(Producto producto, int cantidad, Cliente cliente) {
        if (producto == null) return "El producto ha sido eliminado.";
        if (!producto.isEsVisible()) return "El producto ya no está disponible.";
        if (producto.isSoloPremium() && !cliente.isEsPremium()) {
            return MENSAJE_SOLO_PREMIUM;
        }
        if (producto.getStock() <= 0) return "El producto está agotado.";
        if (cantidad <= 0 || cantidad > producto.getStock()) {
            return "No hay stock suficiente para la cantidad solicitada.";
        }
        Double precio = producto.getPrecio();
        if (precio == null || !Double.isFinite(precio) || precio < 0) {
            return MENSAJE_PRECIO_NO_DISPONIBLE;
        }
        Double precioAplicable = ProductoService.calcularPrecioParaCliente(producto, cliente.isEsPremium());
        if (precioAplicable == null || !Double.isFinite(precioAplicable) || precioAplicable < 0) {
            return MENSAJE_PRECIO_NO_DISPONIBLE;
        }
        return null;
    }

    private LineaCarritoResponse calcularLinea(Producto producto, int cantidad, Cliente cliente) {
        Double precio = ProductoService.calcularPrecioParaCliente(producto, cliente.isEsPremium());
        if (precio == null || !Double.isFinite(precio) || precio < 0) {
            throw new IllegalArgumentException(MENSAJE_PRECIO_NO_DISPONIBLE);
        }
        BigDecimal precioUnitario = BigDecimal.valueOf(precio).setScale(2);
        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        return new LineaCarritoResponse(producto.getId(), cantidad, precioUnitario, subtotal,
                producto.getNombre(), producto.getImagen(), producto.getStock(), true, null,
                producto.getVendedor());
    }

    private Resultado actualizarCarrito(String idProducto, int cantidad) {
        Cliente cliente = obtenerClienteAutenticado();
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        if (idProducto == null || idProducto.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }

        return reintentarSiHayConflicto(() -> {
            Producto producto = productoDAO.findById(idProducto)
                    .orElseThrow(ProductoNoEncontradoException::new);
            if (!producto.isEsVisible()) {
                throw new IllegalArgumentException("El producto ya no está disponible.");
            }
            if (producto.isSoloPremium() && !cliente.isEsPremium()) {
                throw new AccessDeniedException(MENSAJE_SOLO_PREMIUM);
            }

            Carrito carrito = carritoDAO.findById(cliente.getId())
                    .orElseGet(() -> new Carrito(cliente.getId()));
            long cantidadFinal = (long) carrito.cantidadDe(idProducto) + cantidad;
            if (cantidadFinal > producto.getStock()) {
                throw new IllegalArgumentException("No hay stock suficiente para la cantidad total solicitada.");
            }

            Carrito actualizado = carrito.conCantidad(idProducto, (int) cantidadFinal);
            CarritoResponse respuesta = calcularImporte(actualizado, cliente, producto);
            return new Resultado(carritoDAO.save(actualizado), respuesta);
        });
    }

    private record Resultado(Carrito carrito, CarritoResponse respuesta) {
    }

    private CarritoResponse calcularImporte(Carrito carrito, Cliente cliente, Producto productoAnadido) {
        List<LineaCarritoResponse> lineas = new ArrayList<>();
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        for (var entrada : carrito.getProductos().entrySet()) {
            Producto producto = entrada.getKey().equals(productoAnadido.getId())
                    ? productoAnadido
                    : productoDAO.findById(entrada.getKey()).orElseThrow(ProductoNoEncontradoException::new);
            if (!producto.isEsVisible()) {
                throw new IllegalArgumentException("Un producto del carrito ya no está disponible.");
            }
            if (producto.isSoloPremium() && !cliente.isEsPremium()) {
                throw new AccessDeniedException("Un producto del carrito solo está disponible para clientes Premium.");
            }
            if (entrada.getValue() <= 0 || entrada.getValue() > producto.getStock()) {
                throw new IllegalArgumentException("No hay stock suficiente para un producto del carrito.");
            }
            LineaCarritoResponse linea = calcularLinea(producto, entrada.getValue(), cliente);
            lineas.add(linea);
            importe = importe.add(linea.subtotal());
        }
        return new CarritoResponse(List.copyOf(lineas), importe);
    }

    LineaCarritoResponse validarLineaParaCompra(Producto producto, int cantidad, Cliente cliente) {
        String aviso = comprobarDisponibilidad(producto, cantidad, cliente);
        if (aviso != null) throw new IllegalArgumentException(aviso);
        return calcularLinea(producto, cantidad, cliente);
    }

    Cliente obtenerClienteAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "ROLE_CLIENTE".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Solo un cliente autenticado puede modificar su carrito.");
        }
        return clienteDAO.findByEmail(authentication.getName())
                .filter(cliente -> cliente.isActivada() && !cliente.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un cliente activo puede modificar su carrito."));
    }
}
