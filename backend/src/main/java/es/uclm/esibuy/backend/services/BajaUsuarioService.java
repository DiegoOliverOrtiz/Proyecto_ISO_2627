package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.exceptions.CuentaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.VendedorConPedidosEnCursoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;

@Service
public class BajaUsuarioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BajaUsuarioService.class);
    private final ClienteDAO clienteDAO;
    private final VendedorDAO vendedorDAO;
    private final CarritoDAO carritoDAO;
    private final ListaProductosDAO listaProductosDAO;
    private final MetodoPagoDAO metodoPagoDAO;
    private final PedidoDAO pedidoDAO;
    private final ProductoDAO productoDAO;
    private final SesionService sesionService;
    private final ProductoImagenService productoImagenService;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transaccion;

    public BajaUsuarioService(
            ClienteDAO clienteDAO,
            VendedorDAO vendedorDAO,
            CarritoDAO carritoDAO,
            ListaProductosDAO listaProductosDAO,
            MetodoPagoDAO metodoPagoDAO,
            PedidoDAO pedidoDAO,
            ProductoDAO productoDAO,
            SesionService sesionService,
            ProductoImagenService productoImagenService,
            AuditoriaService auditoriaService,
            MongoTransactionManager transactionManager) {
        this.clienteDAO = clienteDAO;
        this.vendedorDAO = vendedorDAO;
        this.carritoDAO = carritoDAO;
        this.listaProductosDAO = listaProductosDAO;
        this.metodoPagoDAO = metodoPagoDAO;
        this.pedidoDAO = pedidoDAO;
        this.productoDAO = productoDAO;
        this.sesionService = sesionService;
        this.productoImagenService = productoImagenService;
        this.auditoriaService = auditoriaService;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    public void darDeBajaCliente(String emailAutenticado) {
        String email = normalizarEmail(emailAutenticado);
        transaccion.executeWithoutResult(status -> {
            Cliente cliente = clienteDAO.findByEmail(email).orElseThrow(CuentaNoEncontradaException::new);
            for (Pedido pedido : pedidoDAO.findByClienteId(cliente.getId())) {
                pedidoDAO.save(pedido.desvinculadoCliente());
            }
            listaProductosDAO.deleteByUsuarioId(cliente.getId());
            metodoPagoDAO.deleteByClienteId(cliente.getId());
            carritoDAO.deleteById(cliente.getId());
            clienteDAO.delete(cliente);
        });
        sesionService.cerrarSesiones(email);
        auditoriaService.registrarBajaCuenta("CLIENTE");
    }

    public void darDeBajaVendedor(String emailAutenticado) {
        String email = normalizarEmail(emailAutenticado);
        List<String> imagenes = transaccion.execute(status -> {
            Vendedor vendedor = vendedorDAO.findByEmail(email).orElseThrow(CuentaNoEncontradaException::new);
            List<Pedido> pedidos = pedidoDAO.findByVendedorId(vendedor.getId());
            if (pedidos.stream().anyMatch(pedido -> pedido.getEstado() != EstadoPedido.FINALIZADO)) {
                throw new VendedorConPedidosEnCursoException();
            }

            List<Producto> productos = productoDAO.findByVendedor(vendedor.getNombreComercial());
            List<String> rutasImagenes = productos.stream()
                    .map(Producto::getImagen)
                    .filter(ruta -> ruta != null && !ruta.isBlank())
                    .toList();
            productoDAO.deleteAll(productos);
            listaProductosDAO.deleteByUsuarioId(vendedor.getId());
            vendedorDAO.delete(vendedor);
            return rutasImagenes;
        });

        for (String imagen : imagenes) {
            try {
                productoImagenService.eliminar(imagen);
            } catch (RuntimeException excepcion) {
                // La baja de Mongo ya ha terminado; un fallo al limpiar un archivo no debe restaurar la cuenta.
                LOGGER.warn("No se pudo limpiar una imagen de un producto eliminado.");
            }
        }
        sesionService.cerrarSesiones(email);
        auditoriaService.registrarBajaCuenta("VENDEDOR");
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CuentaNoEncontradaException();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
