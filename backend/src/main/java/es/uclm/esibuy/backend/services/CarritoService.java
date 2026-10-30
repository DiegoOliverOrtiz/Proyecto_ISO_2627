package es.uclm.esibuy.backend.services;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
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

            try {
                return carritoDAO.save(carrito.conCantidad(idProducto, (int) cantidadFinal));
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                // También cubre dos primeras altas simultáneas del mismo carrito.
                if (intento == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No se ha podido actualizar el carrito.");
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
