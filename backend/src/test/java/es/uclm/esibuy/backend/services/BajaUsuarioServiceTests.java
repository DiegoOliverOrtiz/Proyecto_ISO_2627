package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ListaProductosDAO;
import es.uclm.esibuy.backend.dao.MetodoPagoDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.exceptions.VendedorConPedidosEnCursoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;

@ExtendWith(MockitoExtension.class)
class BajaUsuarioServiceTests {
    private static final String EMAIL_CLIENTE = "ana@example.com";
    private static final String EMAIL_VENDEDOR = "tienda@example.com";

    @Mock private ClienteDAO clientes;
    @Mock private VendedorDAO vendedores;
    @Mock private CarritoDAO carritos;
    @Mock private ListaProductosDAO listas;
    @Mock private MetodoPagoDAO metodos;
    @Mock private PedidoDAO pedidos;
    @Mock private ProductoDAO productos;
    @Mock private SesionService sesiones;
    @Mock private ProductoImagenService imagenes;
    @Mock private AuditoriaService auditoria;
    @Mock private MongoTransactionManager transacciones;

    private BajaUsuarioService servicio;

    @BeforeEach
    void configurar() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        servicio = new BajaUsuarioService(clientes, vendedores, carritos, listas, metodos, pedidos, productos,
                sesiones, imagenes, auditoria, transacciones);
    }

    @Test
    void bajaClienteAnonimizaPedidosYEliminaLosDatosAsociadosAntesDeCerrarSesiones() {
        Cliente cliente = cliente();
        Pedido pedido = pedido(EstadoPedido.PAGADO_COMPLETADO);
        when(clientes.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cliente));
        when(pedidos.findByClienteId("cliente-1")).thenReturn(List.of(pedido));

        servicio.darDeBajaCliente("  ANA@EXAMPLE.COM ");

        var captor = org.mockito.ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).save(captor.capture());
        Pedido anonimizado = captor.getValue();
        assertNull(anonimizado.getClienteId());
        assertEquals("cliente eliminado", anonimizado.getClienteNombre());
        assertNull(anonimizado.getDireccionEnvio());
        assertNull(anonimizado.getMetodoPagoId());
        assertEquals(pedido.getLineas(), anonimizado.getLineas());
        InOrder orden = inOrder(listas, metodos, carritos, clientes, sesiones, auditoria);
        orden.verify(listas).deleteByUsuarioId("cliente-1");
        orden.verify(metodos).deleteByClienteId("cliente-1");
        orden.verify(carritos).deleteById("cliente-1");
        orden.verify(clientes).delete(cliente);
        orden.verify(sesiones).cerrarSesiones(EMAIL_CLIENTE);
        orden.verify(auditoria).registrarBajaCuenta("CLIENTE");
    }

    @Test
    void siFallaLaEliminacionDelClienteNoInvalidaSesionesNiRegistraBaja() {
        Cliente cliente = cliente();
        when(clientes.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cliente));
        org.mockito.Mockito.doThrow(new IllegalStateException("fallo de persistencia"))
                .when(clientes).delete(cliente);

        assertThrows(IllegalStateException.class, () -> servicio.darDeBajaCliente(EMAIL_CLIENTE));

        verify(sesiones, never()).cerrarSesiones(any());
        verifyNoInteractions(auditoria);
        verify(transacciones).rollback(any());
    }

    @Test
    void bloqueaLaBajaDelVendedorAntesDeEliminarProductosSiHayUnPedidoNoFinalizado() {
        Vendedor vendedor = vendedor();
        when(vendedores.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(vendedor));
        when(pedidos.findByVendedorId("vendedor-1"))
                .thenReturn(List.of(pedido(EstadoPedido.PAGADO_COMPLETADO)));

        assertThrows(VendedorConPedidosEnCursoException.class,
                () -> servicio.darDeBajaVendedor(EMAIL_VENDEDOR));

        verify(productos, never()).findByVendedor(any());
        verify(productos, never()).deleteAll(any(Iterable.class));
        verify(vendedores, never()).delete(any(Vendedor.class));
        verify(sesiones, never()).cerrarSesiones(any());
        verify(transacciones).rollback(any());
    }

    @Test
    void permiteBajaDelVendedorConPedidosFinalizadosYConservaElHistorico() {
        Vendedor vendedor = vendedor();
        Producto producto = new Producto();
        producto.setId("producto-1");
        producto.setImagen("/product-images/producto.png");
        when(vendedores.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(vendedor));
        when(pedidos.findByVendedorId("vendedor-1"))
                .thenReturn(List.of(pedido(EstadoPedido.FINALIZADO)));
        when(productos.findByVendedor("Tienda Uno")).thenReturn(List.of(producto));

        servicio.darDeBajaVendedor(EMAIL_VENDEDOR);

        InOrder orden = inOrder(productos, listas, vendedores, sesiones, auditoria);
        orden.verify(productos).deleteAll(List.of(producto));
        orden.verify(listas).deleteByUsuarioId("vendedor-1");
        orden.verify(vendedores).delete(vendedor);
        orden.verify(sesiones).cerrarSesiones(EMAIL_VENDEDOR);
        orden.verify(auditoria).registrarBajaCuenta("VENDEDOR");
        verify(imagenes).eliminar("/product-images/producto.png");
        verify(pedidos, never()).delete(any(Pedido.class));
        verify(pedidos, never()).deleteAll(any(Iterable.class));
    }

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setEmail(EMAIL_CLIENTE);
        return cliente;
    }

    private Vendedor vendedor() {
        Vendedor vendedor = new Vendedor();
        vendedor.setId("vendedor-1");
        vendedor.setEmail(EMAIL_VENDEDOR);
        vendedor.setNombreComercial("Tienda Uno");
        return vendedor;
    }

    private Pedido pedido(EstadoPedido estado) {
        Direccion direccion = new Direccion();
        direccion.setId("direccion-1");
        direccion.setCalle("Calle Privada");
        return Pedido.builder().id("pedido-1").clienteId("cliente-1")
                .lineas(List.of(new LineaPedido("producto-1", "Producto histórico", new BigDecimal("4.25"),
                        1, "vendedor-1", "Tienda Uno")))
                .estado(estado).importe(new BigDecimal("4.25"))
                .fechaCreacion(Instant.parse("2026-10-01T10:00:00Z"))
                .direccionEnvio(direccion).metodoPagoId("metodo-1").build();
    }
}
