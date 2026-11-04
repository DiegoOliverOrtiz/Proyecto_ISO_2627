package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.DireccionResponse;
import es.uclm.esibuy.backend.dto.LineaPedidoVendedorResponse;
import es.uclm.esibuy.backend.dto.PedidoVendedorResponse;
import es.uclm.esibuy.backend.exceptions.PedidoVendedorNoEncontradoException;
import es.uclm.esibuy.backend.exceptions.TransicionEstadoPedidoException;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Vendedor;

@Service
public class PedidosVendedorService {

    private final PedidoDAO pedidoDAO;
    private final VendedorDAO vendedorDAO;

    public PedidosVendedorService(
            PedidoDAO pedidoDAO,
            VendedorDAO vendedorDAO) {

        this.pedidoDAO = pedidoDAO;
        this.vendedorDAO = vendedorDAO;
    }

    public List<PedidoVendedorResponse> consultarActivos(
            String emailAutenticado) {

        String vendedorId = obtenerVendedorActivo(
                emailAutenticado
        ).getId();

        return consultarPedidos(vendedorId, false);
    }

    public List<PedidoVendedorResponse> consultarHistorico(
            String emailAutenticado) {

        String vendedorId = obtenerVendedorActivo(
                emailAutenticado
        ).getId();

        return consultarPedidos(vendedorId, true);
    }

    public PedidoVendedorResponse consultarDetalle(
            String emailAutenticado,
            String pedidoId) {

        String vendedorId = obtenerVendedorActivo(
                emailAutenticado
        ).getId();

        if (pedidoId == null || pedidoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del pedido es obligatorio."
            );
        }

        Pedido pedido = pedidoDAO.findById(pedidoId)
                .orElseThrow(PedidoVendedorNoEncontradoException::new);

        if (!esVisibleParaVendedor(pedido, vendedorId)) {
            throw new PedidoVendedorNoEncontradoException();
        }

        return convertirRespuesta(pedido, vendedorId);
    }

    public PedidoVendedorResponse aceptarPedido(
            String emailAutenticado,
            String pedidoId) {

        String vendedorId = obtenerVendedorActivo(
                emailAutenticado
        ).getId();

        if (pedidoId == null || pedidoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador del pedido es obligatorio."
            );
        }

        Pedido pedido = pedidoDAO.findById(pedidoId)
                .orElseThrow(PedidoVendedorNoEncontradoException::new);

        if (!esVisibleParaVendedor(pedido, vendedorId)) {
            throw new PedidoVendedorNoEncontradoException();
        }

        EstadoPedido destino = EstadoPedido.TRAMITADO_ENVIADO;

        if (!pedido.getEstado().permiteTransicionA(destino)) {
            throw new TransicionEstadoPedidoException();
        }

        // Si el cliente lo canceló u otro vendedor lo aceptó entre medias, no se modifica nada.
        long actualizados = pedidoDAO.actualizarEstado(
                pedidoId,
                pedido.getEstado().name(),
                vendedorId,
                destino.name()
        );

        if (actualizados == 0) {
            throw new TransicionEstadoPedidoException();
        }

        Pedido aceptado = pedidoDAO.findById(pedidoId)
                .orElseThrow(PedidoVendedorNoEncontradoException::new);

        return convertirRespuesta(aceptado, vendedorId);
    }

    private Vendedor obtenerVendedorActivo(String correo) {

        if (correo == null || correo.isBlank()) {
            throw new AccessDeniedException(
                    "Solo un vendedor activo puede consultar pedidos."
            );
        }

        return vendedorDAO.findByEmail(correo)
                .filter(vendedor ->
                        vendedor.isActivada()
                        && !vendedor.isBloqueada())
                .orElseThrow(() -> new AccessDeniedException(
                        "Solo un vendedor activo puede consultar pedidos."
                ));
    }

    private List<PedidoVendedorResponse> consultarPedidos(
            String vendedorId,
            boolean historico) {

        return pedidoDAO.findByVendedorId(vendedorId)
                .stream()
                .filter(pedido ->
                        esVisibleParaVendedor(pedido, vendedorId))
                .filter(pedido -> historico
                        ? pedido.getEstado() == EstadoPedido.FINALIZADO
                        : esActivo(pedido.getEstado()))
                .sorted(
                        Comparator.comparing(
                                Pedido::getFechaPago,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .map(pedido ->
                        convertirRespuesta(pedido, vendedorId))
                .toList();
    }

    private boolean esActivo(EstadoPedido estado) {
        return estado == EstadoPedido.PAGADO_COMPLETADO
                || estado == EstadoPedido.TRAMITADO_ENVIADO;
    }

    private boolean esVisibleParaVendedor(
            Pedido pedido,
            String vendedorId) {

        if (pedido.getEstado() == EstadoPedido.EN_PROCESO) {
            return false;
        }

        return pedido.getLineas().stream()
                .anyMatch(linea ->
                        Objects.equals(
                                linea.vendedorId(),
                                vendedorId
                        )
                );
    }

    private PedidoVendedorResponse convertirRespuesta(
            Pedido pedido,
            String vendedorId) {

        List<LineaPedido> propias = pedido.getLineas()
                .stream()
                .filter(linea ->
                        Objects.equals(
                                linea.vendedorId(),
                                vendedorId
                        )
                )
                .toList();

        List<LineaPedidoVendedorResponse> productos =
                propias.stream()
                        .map(LineaPedidoVendedorResponse::de)
                        .toList();

        BigDecimal importePropio = propias.stream()
                .map(linea ->
                        linea.precioCompra().multiply(
                                BigDecimal.valueOf(linea.cantidad())
                        )
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Direccion direccion = pedido.getDireccionEnvio();

        DireccionResponse direccionResponse =
                direccion == null
                        ? null
                        : DireccionResponse.de(direccion);

        return new PedidoVendedorResponse(
                pedido.getId(),
                pedido.getClienteNombre() == null || pedido.getClienteNombre().isBlank()
                        ? "Cliente"
                        : pedido.getClienteNombre(),
                pedido.getEstado(),
                pedido.getFechaCreacion(),
                pedido.getFechaPago(),
                productos,
                importePropio,
                direccionResponse
        );
    }
}
