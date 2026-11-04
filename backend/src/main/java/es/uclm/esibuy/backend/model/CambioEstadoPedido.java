package es.uclm.esibuy.backend.model;

import java.time.Instant;

public record CambioEstadoPedido(EstadoPedido estado, Instant fecha) {
}
