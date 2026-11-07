package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import es.uclm.esibuy.backend.model.Producto;

/*
 * Precio que paga un cliente. Si es premium y el producto tiene descuento premium, se aplica ese
 * descuento, y si no, el descuento normal del producto.
 */
final class PrecioProducto {

    private PrecioProducto() {
    }

    static Double paraCliente(Producto producto, boolean clientePremium) {
        return calcularPrecioFinal(producto.getPrecio(), descuentoAplicable(producto, clientePremium));
    }

    static int descuentoAplicable(Producto producto, boolean clientePremium) {
        return clientePremium && producto.getDescuentoPremium() > 0
                ? producto.getDescuentoPremium()
                : producto.getDescuento();
    }

    private static Double calcularPrecioFinal(Double precio, int descuento) {
        if (precio == null) {
            return null;
        }
        return BigDecimal.valueOf(precio)
                .multiply(BigDecimal.valueOf(100L - descuento))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
