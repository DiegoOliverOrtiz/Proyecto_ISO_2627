package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;

public record LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal,
        String nombre, Integer stock, boolean disponible, String aviso) {
    public LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this(productoId, cantidad, precioUnitario, subtotal, null, null, true, null);
    }
}
