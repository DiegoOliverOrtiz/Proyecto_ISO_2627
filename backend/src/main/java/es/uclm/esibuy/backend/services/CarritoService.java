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
            Double precio = ProductoService.calcularPrecioParaCliente(producto, cliente.isEsPremium());
            if (precio == null || !Double.isFinite(precio) || precio < 0) {
                throw new IllegalArgumentException("Un producto del carrito no tiene un precio disponible.");
            }
            BigDecimal precioUnitario = BigDecimal.valueOf(precio).setScale(2);
            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(entrada.getValue()));
            lineas.add(new LineaCarritoResponse(producto.getId(), entrada.getValue(), precioUnitario, subtotal));
            importe = importe.add(subtotal);
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
