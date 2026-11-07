package es.uclm.esibuy.backend.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.exceptions.OperacionPedidoConflictivaException;
import es.uclm.esibuy.backend.model.Carrito;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;

/*
 * HU-26 - Compra del carrito dentro de la transacción que abre PedidoService: crea el pedido,
 * descuenta el stock, lo marca como pagado (pago simulado) y vacía el carrito.
 */
final class CompraPedido {
    private final CarritoService carritoService;
    private final CarritoDAO carritoDAO;
    private final ProductoDAO productoDAO;
    private final MetodoPagoDAO metodoPagoDAO;
    private final VendedorDAO vendedorDAO;
    private final PedidoDAO pedidoDAO;

    CompraPedido(CarritoService carritoService, CarritoDAO carritoDAO, ProductoDAO productoDAO,
            MetodoPagoDAO metodoPagoDAO, VendedorDAO vendedorDAO, PedidoDAO pedidoDAO) {
        this.carritoService = carritoService;
        this.carritoDAO = carritoDAO;
        this.productoDAO = productoDAO;
        this.metodoPagoDAO = metodoPagoDAO;
        this.vendedorDAO = vendedorDAO;
        this.pedidoDAO = pedidoDAO;
    }

    Pedido comprar(String direccionId, String metodoPagoId, String operacionId) {
        Cliente cliente = carritoService.obtenerClienteAutenticado();
        String pedidoId = IdempotenciaPedido.identificadorPedido(cliente.getId(), operacionId);
        Pedido anterior = pedidoDAO.findById(pedidoId).orElse(null);
        if (anterior != null) {
            if (!cliente.getId().equals(anterior.getClienteId())
                    || !Objects.equals(metodoPagoId, anterior.getMetodoPagoId())
                    || anterior.getDireccionEnvio() == null
                    || !direccionId.equals(anterior.getDireccionEnvio().getId())
                    || anterior.getEstado() != EstadoPedido.PAGADO_COMPLETADO) {
                throw new OperacionPedidoConflictivaException();
            }
            return anterior;
        }
        Direccion direccion = cliente.buscarDireccion(direccionId)
                .orElseThrow(() -> new IllegalArgumentException("La dirección no pertenece al cliente o no existe."));
        boolean metodoPropio = metodoPagoDAO.findByClienteId(cliente.getId()).stream()
                .anyMatch(metodo -> metodoPagoId.equals(metodo.getId())
                        && cliente.getId().equals(metodo.getClienteId()));
        if (!metodoPropio) {
            throw new IllegalArgumentException("El método de pago no pertenece al cliente o no existe.");
        }
        Carrito carrito = carritoDAO.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("El carrito está vacío."));
        if (carrito.getProductos().isEmpty()) throw new IllegalArgumentException("El carrito está vacío.");

        List<LineaPedido> lineas = new ArrayList<>();
        List<Producto> productos = new ArrayList<>();
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        // Orden estable para reducir conflictos entre compras con varios productos.
        for (var entrada : carrito.getProductos().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Producto producto = productoDAO.findById(entrada.getKey()).orElse(null);
            var calculo = carritoService.validarLineaParaCompra(producto, entrada.getValue(), cliente);
            if (producto.getVendedor() == null || producto.getVendedor().isBlank()) {
                throw new IllegalArgumentException("El producto no tiene un vendedor válido.");
            }
            Vendedor vendedor = vendedorDAO.findByNombreComercial(producto.getVendedor())
                    .orElseThrow(() -> new IllegalArgumentException("El vendedor del producto ya no existe."));
            lineas.add(new LineaPedido(producto.getId(), producto.getNombre(), calculo.precioUnitario(),
                    entrada.getValue(), vendedor.getId(), vendedor.getNombreComercial()));
            productos.add(producto);
            importe = importe.add(calculo.subtotal());
        }
        Pedido pedido = Pedido.builder()
                .id(pedidoId)
                .clienteId(cliente.getId())
                .lineas(lineas)
                .estado(EstadoPedido.EN_PROCESO)
                .importe(importe)
                .fechaCreacion(Instant.now())
                .direccionEnvio(direccion)
                .metodoPagoId(metodoPagoId)
                .build();
        pedido.setClienteNombre((cliente.getNombre() + " " + cliente.getApellidos()).trim());
        pedidoDAO.insert(pedido);
        for (int i = 0; i < productos.size(); i++) {
            productoDAO.descontarStock(productos.get(i), lineas.get(i).cantidad());
        }
        Pedido pagado = pedido.pagado(Instant.now());
        pedidoDAO.save(pagado);
        carritoDAO.save(carrito.vacio());
        return pagado;
    }
}
