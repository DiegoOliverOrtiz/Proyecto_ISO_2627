package es.uclm.esibuy.backend.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.exceptions.ProductoAjenoException;
import es.uclm.esibuy.backend.exceptions.ProductoConPedidosEnCursoException;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;

/*
 * HU-33 - Reglas para eliminar un producto y limpieza de su imagen.
 */
final class EliminacionProducto {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductoService.class);
    // Pedidos en curso que impiden eliminar un producto: los carritos (EN_PROCESO) y los finalizados no cuentan.
    private static final List<String> ESTADOS_PEDIDO_EN_CURSO = List.of(
            EstadoPedido.PAGADO_COMPLETADO.name(), EstadoPedido.TRAMITADO_ENVIADO.name());

    private EliminacionProducto() {
    }

    static void comprobarEliminable(Vendedor vendedor, Producto producto, PedidoDAO pedidoDAO) {
        if (!vendedor.getNombreComercial().equals(producto.getVendedor())) {
            throw new ProductoAjenoException();
        }
        // Los pedidos finalizados no lo impiden: sus líneas guardan los datos del producto (T-26.3).
        if (pedidoDAO.existsByProductoIdAndEstadoIn(producto.getId(), ESTADOS_PEDIDO_EN_CURSO)) {
            throw new ProductoConPedidosEnCursoException();
        }
    }

    // La imagen se borra después del producto: si falla, solo queda un archivo huérfano.
    static void eliminarImagen(ProductoImagenService productoImagenService, String imagen) {
        try {
            productoImagenService.eliminar(imagen);
        } catch (RuntimeException exception) {
            LOGGER.warn("No se pudo limpiar la imagen de un producto eliminado.");
        }
    }
}
