package es.uclm.esibuy.backend.dto;

import java.time.Instant;

import es.uclm.esibuy.backend.model.CambioEstadoPedido;
import es.uclm.esibuy.backend.model.EstadoPedido;

public record CambioEstadoPedidoResponse(EstadoPedido estado, Instant fecha) {
    public static CambioEstadoPedidoResponse de(CambioEstadoPedido cambio) {
        return new CambioEstadoPedidoResponse(cambio.estado(), cambio.fecha());
    }
}