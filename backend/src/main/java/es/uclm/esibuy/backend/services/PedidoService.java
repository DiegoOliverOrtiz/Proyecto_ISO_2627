package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;


import com.mongodb.MongoException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.exceptions.CancelacionPedidoException;
import es.uclm.esibuy.backend.exceptions.OperacionPedidoConflictivaException;
import es.uclm.esibuy.backend.model.Carrito;
import es.uclm.esibuy.backend.model.CambioEstadoPedido;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.dto.PedidoConsultaResponse;

@Service
public class PedidoService {
    private final CarritoService carritoService;
    private final CarritoDAO carritoDAO;
    private final ProductoDAO productoDAO;
    private final MetodoPagoDAO metodoPagoDAO;
    private final VendedorDAO vendedorDAO;
    private final PedidoDAO pedidoDAO;
    private final TransactionTemplate transaccion;

    public PedidoService(CarritoService carritoService, CarritoDAO carritoDAO, ProductoDAO productoDAO,
            MetodoPagoDAO metodoPagoDAO, VendedorDAO vendedorDAO, PedidoDAO pedidoDAO,
            MongoTransactionManager pedidoTransactionManager) {
        this.carritoService = carritoService;
        this.carritoDAO = carritoDAO;
        this.productoDAO = productoDAO;
        this.metodoPagoDAO = metodoPagoDAO;
        this.vendedorDAO = vendedorDAO;
        this.pedidoDAO = pedidoDAO;
        this.transaccion = new TransactionTemplate(pedidoTransactionManager);
    }

    public Pedido realizarPedido(String direccionId, String metodoPagoId, String operacionId) {
        validarIdentificador(direccionId, "La dirección es obligatoria.");
        validarIdentificador(metodoPagoId, "El método de pago es obligatorio.");
        if (operacionId == null || !operacionId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("La clave de operación debe ser un UUID.");
        }
        for (int intento = 0; ; intento++) {
            try {
                return transaccion.execute(status -> comprar(direccionId, metodoPagoId,
                        UUID.fromString(operacionId).toString()));
            } catch (RuntimeException exception) {
                // Nunca repetir el cuerpo si se desconoce el resultado del commit.
                if (intento >= 2 || !esTransitoria(exception)) throw exception;
            }
        }
    }

    public PedidoConsultaResponse cancelarPedido(String pedidoId) {
        validarIdentificador(pedidoId, "El identificador del pedido es obligatorio.");
        Cliente cliente = carritoService.obtenerClienteAutenticado();

        for (int intento = 0; ; intento++) {
            try {
                return transaccion.execute(status ->
                        cancelarPedidoEnTransaccion(pedidoId, cliente.getId()));
            } catch (RuntimeException exception) {
                if (intento >= 2 || !esTransitoria(exception)) {
                    throw exception;
                }
            }
        }
    }

    public PedidoConsultaResponse confirmarRecepcion(String pedidoId) {
        validarIdentificador(
                pedidoId, "El identificador del pedido es obligatorio.");

        Cliente cliente = carritoService.obtenerClienteAutenticado();

        for (int intento = 0; ; intento++) {
            try {
                return transaccion.execute(status ->
                        confirmarRecepcionEnTransaccion(
                                pedidoId, cliente.getId()));
            } catch (RuntimeException exception) {
                if (intento >= 2 || !esTransitoria(exception)) {
                    throw exception;
                }
            }
        }
    }

    private PedidoConsultaResponse confirmarRecepcionEnTransaccion(
            String pedidoId, String clienteId) {

        Pedido pedido = pedidoDAO.findByIdAndClienteId(
                pedidoId, clienteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido no encontrado."));

        if (pedido.getEstado() != EstadoPedido.TRAMITADO_ENVIADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se puede confirmar la recepción de un pedido enviado.");
        }

        Pedido finalizado = pedido.cambiarEstado(
                EstadoPedido.FINALIZADO, Instant.now());

        long modificados = pedidoDAO.finalizarPedido(
                pedidoId,
                clienteId,
                EstadoPedido.TRAMITADO_ENVIADO.name(),
                EstadoPedido.FINALIZADO.name(),
                finalizado.getHistorialEstados());

        if (modificados != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El pedido ha cambiado. Actualízalo e inténtalo de nuevo.");
        }

        return PedidoConsultaResponse.de(finalizado);
    }

    private PedidoConsultaResponse cancelarPedidoEnTransaccion(
            String pedidoId,
            String clienteId) {
        Pedido pedido = pedidoDAO.findByIdAndClienteId(pedidoId, clienteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido no encontrado."));

        EstadoPedido estadoActual = pedido.getEstado();
        if (estadoActual == null || !estadoActual.permiteTransicionA(EstadoPedido.CANCELADO)) {
            String motivo = estadoActual == null
                    ? "El pedido no tiene un estado válido y no se puede cancelar."
                    : switch (estadoActual) {
                        case EN_PROCESO, PAGADO_COMPLETADO ->
                                "El pedido ha cambiado mientras se cancelaba. Actualízalo e inténtalo de nuevo.";
                        case TRAMITADO_ENVIADO ->
                                "No se puede cancelar: el pedido ya ha sido enviado.";
                        case FINALIZADO ->
                                "No se puede cancelar: el pedido ya está finalizado.";
                        case CANCELADO ->
                                "El pedido ya ha sido cancelado.";
                    };
            throw new CancelacionPedidoException(motivo);
        }

        Pedido cancelado = pedido.cancelar(Instant.now());
        long modificados = pedidoDAO.cancelarPedido(
                pedidoId,
                clienteId,
                estadoActual.name(),
                EstadoPedido.CANCELADO.name(),
                cancelado.getHistorialEstados());
        if (modificados != 1) {
            throw new CancelacionPedidoException(
                    "El pedido ha cambiado mientras se cancelaba. Actualízalo e inténtalo de nuevo.");
        }

        if (estadoActual == EstadoPedido.PAGADO_COMPLETADO) {
            for (LineaPedido linea : pedido.getLineas()) {
                productoDAO.reponerStock(linea.productoId(), linea.cantidad());
            }
        }

        return PedidoConsultaResponse.de(cancelado);
    }

    private Pedido comprar(String direccionId, String metodoPagoId, String operacionId) {
        Cliente cliente = carritoService.obtenerClienteAutenticado();
        String pedidoId = identificadorPedido(cliente.getId(), operacionId);
        Pedido anterior = pedidoDAO.findById(pedidoId).orElse(null);
        if (anterior != null) {
            if (!cliente.getId().equals(anterior.getClienteId())
                    || !Objects.equals(metodoPagoId, anterior.getMetodoPagoId())
                    || anterior.getDireccionEnvio() == null
                    || !direccionId.equals(anterior.getDireccionEnvio().getId())
                    || anterior.getEstado() != EstadoPedido.PAGADO_COMPLETADO) {
                throw new OperacionPedidoConflictivaException();
            }
            return anterior;
        }
        Direccion direccion = cliente.buscarDireccion(direccionId)
                .orElseThrow(() -> new IllegalArgumentException("La dirección no pertenece al cliente o no existe."));
        boolean metodoPropio = metodoPagoDAO.findByClienteId(cliente.getId()).stream()
                .anyMatch(metodo -> metodoPagoId.equals(metodo.getId())
                        && cliente.getId().equals(metodo.getClienteId()));
        if (!metodoPropio) {
            throw new IllegalArgumentException("El método de pago no pertenece al cliente o no existe.");
        }
        Carrito carrito = carritoDAO.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("El carrito está vacío."));
        if (carrito.getProductos().isEmpty()) throw new IllegalArgumentException("El carrito está vacío.");

        List<LineaPedido> lineas = new ArrayList<>();
        List<Producto> productos = new ArrayList<>();
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        // Orden estable para reducir conflictos entre compras con varios productos.
        for (var entrada : carrito.getProductos().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            Producto producto = productoDAO.findById(entrada.getKey()).orElse(null);
            var calculo = carritoService.validarLineaParaCompra(producto, entrada.getValue(), cliente);
            if (producto.getVendedor() == null || producto.getVendedor().isBlank()) {
                throw new IllegalArgumentException("El producto no tiene un vendedor válido.");
            }
            Vendedor vendedor = vendedorDAO.findByNombreComercial(producto.getVendedor())
                    .orElseThrow(() -> new IllegalArgumentException("El vendedor del producto ya no existe."));
            lineas.add(new LineaPedido(producto.getId(), producto.getNombre(), calculo.precioUnitario(),
                    entrada.getValue(), vendedor.getId(), vendedor.getNombreComercial()));
            productos.add(producto);
            importe = importe.add(calculo.subtotal());
        }
        Pedido pedido = new Pedido(pedidoId, cliente.getId(), lineas, EstadoPedido.EN_PROCESO,
                importe, Instant.now(), null, direccion, metodoPagoId);
        pedido.setClienteNombre((cliente.getNombre() + " " + cliente.getApellidos()).trim());
        pedidoDAO.insert(pedido);
        for (int i = 0; i < productos.size(); i++) {
            productoDAO.descontarStock(productos.get(i), lineas.get(i).cantidad());
        }
        Pedido pagado = pedido.pagado(Instant.now());
        pedidoDAO.save(pagado);
        carritoDAO.save(carrito.vacio());
        return pagado;
    }

    private void validarIdentificador(String id, String mensaje) {
        if (id == null || id.isBlank() || id.length() > 128) throw new IllegalArgumentException(mensaje);
    }

    private String identificadorPedido(String clienteId, String operacionId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((clienteId + ":" + operacionId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean esTransitoria(Throwable exception) {
        boolean transitoria = false;
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongo) {
                if (mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return false;
                transitoria |= mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            }
        }
        return transitoria;
    }

    private static final Set<EstadoPedido> ESTADOS_ACTIVOS = Set.of(
        EstadoPedido.EN_PROCESO,
        EstadoPedido.PAGADO_COMPLETADO,
        EstadoPedido.TRAMITADO_ENVIADO);

    public List<PedidoConsultaResponse> consultarPedidosActivos() {
        Cliente cliente = carritoService.obtenerClienteAutenticado();

        return pedidoDAO.findByClienteId(cliente.getId()).stream()
                .filter(pedido -> ESTADOS_ACTIVOS.contains(pedido.getEstado()))
                .map(PedidoConsultaResponse::de)
                .toList();
    }

    public List<PedidoConsultaResponse> consultarHistoricoPedidos() {
        Cliente cliente = carritoService.obtenerClienteAutenticado();

        return pedidoDAO.findByClienteId(cliente.getId()).stream()
                .filter(pedido -> pedido.getEstado() == EstadoPedido.FINALIZADO)
                .map(PedidoConsultaResponse::de)
                .toList();
    }

    public PedidoConsultaResponse consultarPedido(String pedidoId) {
        Cliente cliente = carritoService.obtenerClienteAutenticado();

        return pedidoDAO.findByIdAndClienteId(pedidoId, cliente.getId())
                .map(PedidoConsultaResponse::de)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido no encontrado."));
    }
}
