package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
        for (int intento = 0; intento < 3; intento++) {
            Carrito carrito = carritoDAO.findById(cliente.getId()).orElse(null);
            if (carrito == null) {
                return presentarCarrito(new Carrito(cliente.getId()), cliente);
            }
            Carrito vacio = carrito.vacio();
            try {
                carritoDAO.save(vacio);
                return presentarCarrito(vacio, cliente);
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                if (intento == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No se ha podido actualizar el carrito.");
    }

    private CarritoResponse modificarLinea(String idProducto, Integer cantidad) {
        Cliente cliente = obtenerClienteAutenticado();
        if (idProducto == null || idProducto.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }
        if (cantidad != null && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        for (int intento = 0; intento < 3; intento++) {
            Carrito carrito = carritoDAO.findById(cliente.getId())
                    .orElseThrow(ProductoNoEncontradoException::new);
            if (!carrito.getProductos().containsKey(idProducto)) {
                throw new ProductoNoEncontradoException();
            }
            Producto productoModificado = null;
            if (cantidad != null) {
                productoModificado = productoDAO.findById(idProducto)
                        .orElseThrow(ProductoNoEncontradoException::new);
                if (productoModificado.isSoloPremium() && !cliente.isEsPremium()) {
                    throw new AccessDeniedException("Este producto solo está disponible para clientes Premium.");
                }
                String aviso = comprobarDisponibilidad(productoModificado, cantidad, cliente);
                if (aviso != null) {
                    throw new IllegalArgumentException(aviso);
                }
            }
            Carrito actualizado = cantidad == null ? carrito.sinProducto(idProducto)
                    : carrito.conCantidad(idProducto, cantidad);
            CarritoResponse respuesta = presentarCarrito(actualizado, cliente, productoModificado);
            try {
                carritoDAO.save(actualizado);
                return respuesta;
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                if (intento == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No se ha podido actualizar el carrito.");
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
            LineaCarritoResponse linea;
            if (aviso == null) {
                linea = calcularLinea(producto, entrada.getValue(), cliente);
                importe = importe.add(linea.subtotal());
            } else {
                linea = new LineaCarritoResponse(entrada.getKey(), entrada.getValue(), null, null,
                        producto == null ? "Producto eliminado" : producto.getNombre(),
                        producto == null ? null : producto.getStock(), false, aviso);
            }
            lineas.add(linea);
        }
        return new CarritoResponse(List.copyOf(lineas), importe);
    }

    private String comprobarDisponibilidad(Producto producto, int cantidad, Cliente cliente) {
        if (producto == null) return "El producto ha sido eliminado.";
        if (!producto.isEsVisible()) return "El producto ya no está disponible.";
        if (producto.isSoloPremium() && !cliente.isEsPremium()) {
            return "Este producto solo está disponible para clientes Premium.";
        }
        if (producto.getStock() <= 0) return "El producto está agotado.";
        if (cantidad <= 0 || cantidad > producto.getStock()) {
            return "No hay stock suficiente para la cantidad solicitada.";
        }
        Double precio = producto.getPrecio();
        if (precio == null || !Double.isFinite(precio) || precio < 0) {
            return "Un producto del carrito no tiene un precio disponible.";
        }
        Double precioAplicable = ProductoService.calcularPrecioParaCliente(producto, cliente.isEsPremium());
        if (precioAplicable == null || !Double.isFinite(precioAplicable) || precioAplicable < 0) {
            return "Un producto del carrito no tiene un precio disponible.";
        }
        return null;
    }

    private LineaCarritoResponse calcularLinea(Producto producto, int cantidad, Cliente cliente) {
        Double precio = ProductoService.calcularPrecioParaCliente(producto, cliente.isEsPremium());
        if (precio == null || !Double.isFinite(precio) || precio < 0) {
            throw new IllegalArgumentException("Un producto del carrito no tiene un precio disponible.");
        }
        BigDecimal precioUnitario = BigDecimal.valueOf(precio).setScale(2);
        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        return new LineaCarritoResponse(producto.getId(), cantidad, precioUnitario, subtotal,
                producto.getNombre(), producto.getStock(), true, null);
    }

    private Resultado actualizarCarrito(String idProducto, int cantidad) {
        Cliente cliente = obtenerClienteAutenticado();
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        if (idProducto == null || idProducto.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }

        for (int intento = 0; intento < 3; intento++) {
            Producto producto = productoDAO.findById(idProducto)
                    .orElseThrow(ProductoNoEncontradoException::new);
            if (!producto.isEsVisible()) {
                throw new IllegalArgumentException("El producto ya no está disponible.");
            }
            if (producto.isSoloPremium() && !cliente.isEsPremium()) {
                throw new AccessDeniedException("Este producto solo está disponible para clientes Premium.");
            }

            Carrito carrito = carritoDAO.findById(cliente.getId())
                    .orElseGet(() -> new Carrito(cliente.getId()));
            long cantidadFinal = (long) carrito.cantidadDe(idProducto) + cantidad;
            if (cantidadFinal > producto.getStock()) {
                throw new IllegalArgumentException("No hay stock suficiente para la cantidad total solicitada.");
            }

            Carrito actualizado = carrito.conCantidad(idProducto, (int) cantidadFinal);
            CarritoResponse respuesta = calcularImporte(actualizado, cliente, producto);
            try {
                return new Resultado(carritoDAO.save(actualizado), respuesta);
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                // También cubre dos primeras altas simultáneas del mismo carrito.
                if (intento == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No se ha podido actualizar el carrito.");
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

    private Cliente obtenerClienteAutenticado() {
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
