package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
