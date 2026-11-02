package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;

public record PedidoResponse(String id, List<LineaPedido> lineas, EstadoPedido estado,
        BigDecimal importe, Instant fechaCreacion, Instant fechaPago,
        DireccionResponse direccionEnvio, String metodoPagoId) {
    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(pedido.getId(), pedido.getLineas(), pedido.getEstado(), pedido.getImporte(),
                pedido.getFechaCreacion(), pedido.getFechaPago(), DireccionResponse.de(pedido.getDireccionEnvio()),
                pedido.getMetodoPagoId());
    }
}
