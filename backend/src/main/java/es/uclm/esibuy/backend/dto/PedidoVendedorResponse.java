package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import es.uclm.esibuy.backend.model.EstadoPedido;

public record PedidoVendedorResponse(
        String id,
        String clienteNombre,
        EstadoPedido estado,
        Instant fechaCreacion,
        Instant fechaPago,
        List<LineaPedidoVendedorResponse> productos,
        BigDecimal importeProductosPropios,
        DireccionResponse direccionEnvio) {
}
