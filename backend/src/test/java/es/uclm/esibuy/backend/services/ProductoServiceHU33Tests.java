package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.exceptions.ProductoAjenoException;
import es.uclm.esibuy.backend.exceptions.ProductoConPedidosEnCursoException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoServiceHU33Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String NOMBRE_COMERCIAL = "Tienda de prueba";
    private static final String ID_PRODUCTO = "producto-1";
    private static final String IMAGEN = "/product-images/teclado.png";
    private static final List<String> ESTADOS_EN_CURSO = List.of("PAGADO_COMPLETADO", "TRAMITADO_ENVIADO");
    private static final Instant FECHA = Instant.parse("2026-11-03T10:00:00Z");

    @Mock
    private ProductoDAO productoDAO;

    @Mock
    private VendedorDAO vendedorDAO;

    @Mock
    private CategoriaDAO categoriaDAO;

    @Mock
    private Validator validator;

    @Mock
    private ProductoImagenService productoImagenService;

    @Mock
    private ClienteDAO clienteDAO;

    @Mock
    private PedidoDAO pedidoDAO;

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(
                productoDAO, vendedorDAO, categoriaDAO, validator, productoImagenService, clienteDAO, pedidoDAO);
    }

    @Test
    void eliminaUnProductoPropioSinPedidosEnCursoYDespuesSuImagen() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        when(pedidoDAO.existsByProductoIdAndEstadoIn(ID_PRODUCTO, ESTADOS_EN_CURSO)).thenReturn(false);

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        InOrder orden = inOrder(productoDAO, productoImagenService);
        orden.verify(productoDAO).delete(producto);
        orden.verify(productoImagenService).eliminar(IMAGEN);
    }

    @Test
    void rechazaEliminarUnProductoConPedidosEnCurso() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto(NOMBRE_COMERCIAL)));
        when(pedidoDAO.existsByProductoIdAndEstadoIn(ID_PRODUCTO, ESTADOS_EN_CURSO)).thenReturn(true);

        assertThrows(
                ProductoConPedidosEnCursoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(productoImagenService);
    }

    @Test
    void rechazaEliminarUnProductoDeOtroVendedor() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto("Otra tienda")));

        assertThrows(
                ProductoAjenoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(pedidoDAO, productoImagenService);
    }

    @Test
    void rechazaEliminarUnProductoInexistente() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.empty());

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(pedidoDAO, productoImagenService);
    }

    @Test
    void unVendedorBloqueadoNoPuedeEliminarProductos() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void unVendedorNoActivadoNoPuedeEliminarProductos() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verifyNoInteractions(productoDAO, pedidoDAO, productoImagenService);
    }

    @Test
    void unFalloAlBorrarLaImagenNoDeshaceLaEliminacion() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        when(pedidoDAO.existsByProductoIdAndEstadoIn(ID_PRODUCTO, ESTADOS_EN_CURSO)).thenReturn(false);
        doThrow(new IllegalStateException("No se pudo eliminar la imagen del producto."))
                .when(productoImagenService).eliminar(IMAGEN);

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
    }

    // Condiciones de satisfacción: la consulta del DAO se simula sobre pedidos reales del modelo.

    @Test
    void sinPedidosElProductoSeEliminaYDejaDeAparecerEnElCatalogoDelVendedor() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        prepararEliminacion(producto, List.of());
        when(productoDAO.findByVendedor(NOMBRE_COMERCIAL)).thenReturn(List.of());

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
        assertTrue(productoService.consultarProductosPropios(EMAIL_VENDEDOR).isEmpty());
    }

    @Test
    void conUnPedidoPagadoNoSeEliminaNiSeTocaLaImagen() {
        prepararEliminacion(crearProducto(NOMBRE_COMERCIAL), List.of(pedidoCon(EstadoPedido.PAGADO_COMPLETADO)));

        ProductoConPedidosEnCursoException error = assertThrows(
                ProductoConPedidosEnCursoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        assertEquals(
                "No se puede eliminar el producto porque figura en pedidos en curso (pagados o enviados).",
                error.getMessage());
        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(productoImagenService);
    }

    @Test
    void conUnPedidoEnviadoNoSeElimina() {
        prepararEliminacion(crearProducto(NOMBRE_COMERCIAL), List.of(pedidoCon(EstadoPedido.TRAMITADO_ENVIADO)));

        assertThrows(
                ProductoConPedidosEnCursoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).delete(any());
    }

    @Test
    void unPedidoFinalizadoEntreOtrosEnCursoNoPermiteEliminar() {
        prepararEliminacion(crearProducto(NOMBRE_COMERCIAL), List.of(
                pedidoCon(EstadoPedido.FINALIZADO), pedidoCon(EstadoPedido.PAGADO_COMPLETADO)));

        assertThrows(
                ProductoConPedidosEnCursoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));
    }

    @Test
    void siSoloFiguraEnPedidosFinalizadosSeEliminaConSuImagen() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        prepararEliminacion(producto, List.of(pedidoCon(EstadoPedido.FINALIZADO), pedidoCon(EstadoPedido.FINALIZADO)));

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
        verify(productoImagenService).eliminar(IMAGEN);
    }

    @Test
    void unCarritoEnProcesoNoImpideEliminar() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        prepararEliminacion(producto, List.of(pedidoCon(EstadoPedido.EN_PROCESO)));

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
    }

    @Test
    void elPedidoHistoricoConservaLosDatosDelProductoEliminado() {
        Pedido finalizado = pedidoCon(EstadoPedido.FINALIZADO);
        prepararEliminacion(crearProducto(NOMBRE_COMERCIAL), List.of(finalizado));

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        // Solo se consulta: el pedido no se modifica ni se borra.
        verify(pedidoDAO).existsByProductoIdAndEstadoIn(ID_PRODUCTO, ESTADOS_EN_CURSO);
        verifyNoMoreInteractions(pedidoDAO);
        LineaPedido linea = finalizado.getLineas().get(0);
        assertEquals(ID_PRODUCTO, linea.productoId());
        assertEquals("Teclado", linea.nombreProducto());
        assertEquals(new BigDecimal("49.99"), linea.precioCompra());
        assertEquals(EstadoPedido.FINALIZADO, finalizado.getEstado());
    }

    @Test
    void losPedidosDeOtrosProductosNoImpidenEliminar() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        Pedido pedidoDeOtroProducto = Pedido.builder().id("pedido-2").clienteId("cliente-1")
                .lineas(List.of(new LineaPedido("otro-producto", "Ratón", new BigDecimal("9.99"), 1,
                        "vendedor-1", NOMBRE_COMERCIAL)))
                .estado(EstadoPedido.PAGADO_COMPLETADO).importe(new BigDecimal("9.99"))
                .fechaCreacion(FECHA).fechaPago(FECHA).metodoPagoId("metodo-1").build();
        prepararEliminacion(producto, List.of(pedidoDeOtroProducto));

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
    }

    @Test
    void unProductoDeOtroVendedorNoSeEliminaAunqueNoTengaPedidos() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto("Otra tienda")));

        ProductoAjenoException error = assertThrows(
                ProductoAjenoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        assertEquals("No puedes eliminar productos de otro vendedor.", error.getMessage());
        verify(productoDAO, never()).delete(any());
        verifyNoInteractions(productoImagenService);
    }

    @Test
    void unProductoYaEliminadoNoSePuedeEliminarDeNuevo() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        prepararEliminacion(producto, List.of());
        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.empty());

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, times(1)).delete(any());
    }

    @Test
    void unProductoSinImagenSeEliminaSinError() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        producto.setImagen(null);
        prepararEliminacion(producto, List.of());

        productoService.eliminarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).delete(producto);
        verify(productoImagenService).eliminar(null);
    }

    private void prepararEliminacion(Producto producto, List<Pedido> pedidos) {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        // Mismo criterio que la consulta de PedidoDAO: alguna línea del producto y estado dentro de la lista.
        lenient().when(pedidoDAO.existsByProductoIdAndEstadoIn(eq(ID_PRODUCTO), anyList()))
                .thenAnswer(invocacion -> {
                    List<String> estados = invocacion.getArgument(1);
                    return pedidos.stream().anyMatch(pedido -> estados.contains(pedido.getEstado().name())
                            && pedido.getLineas().stream().anyMatch(l -> ID_PRODUCTO.equals(l.productoId())));
                });
    }

    private static Pedido pedidoCon(EstadoPedido estado) {
        return Pedido.builder().id("pedido-1").clienteId("cliente-1")
                .lineas(List.of(new LineaPedido(ID_PRODUCTO, "Teclado", new BigDecimal("49.99"), 1,
                        "vendedor-1", NOMBRE_COMERCIAL)))
                .estado(estado).importe(new BigDecimal("49.99")).fechaCreacion(FECHA)
                .fechaPago(estado == EstadoPedido.EN_PROCESO ? null : FECHA).metodoPagoId("metodo-1").build();
    }

    private Vendedor crearVendedor(boolean activado, boolean bloqueado) {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail(EMAIL_VENDEDOR);
        vendedor.setNombreComercial(NOMBRE_COMERCIAL);
        vendedor.setActivada(activado);
        vendedor.setBloqueada(bloqueado);
        return vendedor;
    }

    private Producto crearProducto(String vendedor) {
        Producto producto = new Producto();
        producto.setId(ID_PRODUCTO);
        producto.setNombre("Teclado");
        producto.setCategoria("Informática");
        producto.setStock(4);
        producto.setPrecio(49.99);
        producto.setEsVisible(true);
        producto.setVendedor(vendedor);
        producto.setImagen(IMAGEN);
        return producto;
    }
}
