package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;

/**
 * Línea del carrito. {@code vendedor} es el nombre comercial del vendedor del producto (A8),
 * o {@code null} si el producto se ha eliminado.
 */
public record LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal,
        String nombre, String imagen, Integer stock, boolean disponible, String aviso, String vendedor) {
    public LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this(productoId, cantidad, precioUnitario, subtotal, null, null, null, true, null, null);
    }

    public LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal,
            String nombre, Integer stock, boolean disponible, String aviso) {
        this(productoId, cantidad, precioUnitario, subtotal, nombre, null, stock, disponible, aviso, null);
    }

    public LineaCarritoResponse(String productoId, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal,
            String nombre, String imagen, Integer stock, boolean disponible, String aviso) {
        this(productoId, cantidad, precioUnitario, subtotal, nombre, imagen, stock, disponible, aviso, null);
    }
}
