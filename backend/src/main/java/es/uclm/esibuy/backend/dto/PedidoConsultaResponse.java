package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;

public record PedidoConsultaResponse(
        String id,
        List<LineaPedido> lineas,
        EstadoPedido estado,
        BigDecimal importe,
        Instant fechaCreacion,
        Instant fechaPago,
        DireccionResponse direccionEnvio,
        List<CambioEstadoPedidoResponse> historialEstados) {

    public static PedidoConsultaResponse de(Pedido pedido) {
        DireccionResponse direccion = pedido.getDireccionEnvio() == null
                ? null
                : DireccionResponse.de(pedido.getDireccionEnvio());

        List<CambioEstadoPedidoResponse> historial = pedido.getHistorialEstados().stream()
                .map(CambioEstadoPedidoResponse::de)
                .toList();

        return new PedidoConsultaResponse(
                pedido.getId(),
                pedido.getLineas(),
                pedido.getEstado(),
                pedido.getImporte(),
                pedido.getFechaCreacion(),
                pedido.getFechaPago(),
                direccion,
                historial);
    }
}