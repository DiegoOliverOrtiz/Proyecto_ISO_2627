package es.uclm.esibuy.backend.services;

import java.time.Instant;
import java.util.List;
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
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.dto.PedidoConsultaResponse;

@Service
public class PedidoService {
    private static final String MENSAJE_PEDIDO_NO_ENCONTRADO = "Pedido no encontrado.";
    private final CarritoService carritoService;
    private final ProductoDAO productoDAO;
    private final PedidoDAO pedidoDAO;
    private final TransactionTemplate transaccion;
    private final CompraPedido compra;

    public PedidoService(CarritoService carritoService, CarritoDAO carritoDAO, ProductoDAO productoDAO,
            MetodoPagoDAO metodoPagoDAO, VendedorDAO vendedorDAO, PedidoDAO pedidoDAO,
            MongoTransactionManager pedidoTransactionManager) {
        this.carritoService = carritoService;
        this.productoDAO = productoDAO;
        this.pedidoDAO = pedidoDAO;
        this.transaccion = new TransactionTemplate(pedidoTransactionManager);
        this.compra = new CompraPedido(carritoService, carritoDAO, productoDAO, metodoPagoDAO, vendedorDAO, pedidoDAO);
    }

    public Pedido realizarPedido(String direccionId, String metodoPagoId, String operacionId) {
        validarIdentificador(direccionId, "La dirección es obligatoria.");
        validarIdentificador(metodoPagoId, "El método de pago es obligatorio.");
        String operacion = IdempotenciaPedido.normalizarOperacion(operacionId);
        for (int intento = 0; ; intento++) {
            try {
                return transaccion.execute(status -> compra.comprar(direccionId, metodoPagoId, operacion));
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
                        HttpStatus.NOT_FOUND, MENSAJE_PEDIDO_NO_ENCONTRADO));

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
                        HttpStatus.NOT_FOUND, MENSAJE_PEDIDO_NO_ENCONTRADO));

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

    private void validarIdentificador(String id, String mensaje) {
        if (id == null || id.isBlank() || id.length() > 128) throw new IllegalArgumentException(mensaje);
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
                        HttpStatus.NOT_FOUND, MENSAJE_PEDIDO_NO_ENCONTRADO));
    }
}
