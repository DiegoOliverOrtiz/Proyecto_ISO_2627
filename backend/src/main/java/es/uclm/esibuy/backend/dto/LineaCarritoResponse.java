package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;

public record LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
}
