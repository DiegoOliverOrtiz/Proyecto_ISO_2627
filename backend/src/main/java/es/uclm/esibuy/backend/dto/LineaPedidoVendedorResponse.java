package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;

import es.uclm.esibuy.backend.model.LineaPedido;

public record LineaPedidoVendedorResponse(
        String productoId,
        String nombreProducto,
        BigDecimal precioCompra,
        int cantidad) {

    public static LineaPedidoVendedorResponse de(
            LineaPedido linea) {

        return new LineaPedidoVendedorResponse(
                linea.productoId(),
                linea.nombreProducto(),
                linea.precioCompra(),
                linea.cantidad()
        );
    }
}
