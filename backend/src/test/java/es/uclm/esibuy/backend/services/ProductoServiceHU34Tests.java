package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ProductoAdministracionResponse;
import es.uclm.esibuy.backend.model.Producto;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoServiceHU34Tests {

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
    void devuelveLosProductosDeTodosLosVendedoresConSuVendedor() {
        when(productoDAO.findAll()).thenReturn(List.of(
                crearProducto("p1", "Teclado", "Tienda A", true),
                crearProducto("p2", "Ratón", "Tienda B", true)));

        List<ProductoAdministracionResponse> productos = productoService.consultarProductosParaAdministrador();

        assertEquals(2, productos.size());
        assertEquals("Tienda A", productos.get(0).vendedor());
        assertEquals("Tienda B", productos.get(1).vendedor());
    }

    @Test
    void incluyeLosProductosDesactivadosYMuestraSuEstado() {
        when(productoDAO.findAll()).thenReturn(List.of(
                crearProducto("p1", "Visible", "Tienda A", true),
                crearProducto("p2", "Oculto", "Tienda A", false)));

        List<ProductoAdministracionResponse> productos = productoService.consultarProductosParaAdministrador();

        assertEquals(2, productos.size());
        assertFalse(productos.get(0).esVisible());
        assertEquals("Oculto", productos.get(0).nombre());
        assertTrue(productos.get(1).esVisible());
        verify(productoDAO).findAll();
        verifyNoMoreInteractions(productoDAO);
    }

    @Test
    void muestraLaMismaInformacionQueVeElVendedor() {
        Producto producto = crearProducto("p1", "Teclado", "Tienda A", false);
        producto.setCategoria("Informática");
        producto.setStock(3);
        producto.setDescripcion("Teclado mecánico");
        producto.setPrecio(49.99);
        producto.setDescuento(10);
        producto.setDescuentoPremium(20);
        producto.setImagen("/product-images/teclado.png");
        producto.setSoloPremium(true);
        when(productoDAO.findAll()).thenReturn(List.of(producto));

        ProductoAdministracionResponse respuesta = productoService.consultarProductosParaAdministrador().get(0);

        assertEquals(new ProductoAdministracionResponse(
                "p1", "Teclado", "Informática", 3, "Teclado mecánico", 49.99, 10, 20,
                false, "/product-images/teclado.png", true, "Tienda A"), respuesta);
    }

    @Test
    void ordenaPorVendedorYNombreSinDistinguirMayusculas() {
        when(productoDAO.findAll()).thenReturn(List.of(
                crearProducto("p1", "zapato", "tienda B", true),
                crearProducto("p2", "Bolso", "Tienda A", true),
                crearProducto("p3", "abrigo", "Tienda B", true),
                crearProducto("p4", "Huérfano", null, true)));

        List<String> ids = productoService.consultarProductosParaAdministrador().stream()
                .map(ProductoAdministracionResponse::id)
                .toList();

        assertEquals(List.of("p2", "p3", "p1", "p4"), ids);
    }

    @Test
    void devuelveUnaListaVaciaSiNoHayProductos() {
        when(productoDAO.findAll()).thenReturn(List.of());

        assertTrue(productoService.consultarProductosParaAdministrador().isEmpty());
    }

    @Test
    void noModificaNiConsultaOtrosDatos() {
        when(productoDAO.findAll()).thenReturn(List.of(crearProducto("p1", "Teclado", "Tienda A", true)));

        productoService.consultarProductosParaAdministrador();

        verify(productoDAO).findAll();
        verifyNoMoreInteractions(productoDAO);
        verifyNoInteractions(vendedorDAO, categoriaDAO, productoImagenService, clienteDAO, pedidoDAO);
    }

    private static Producto crearProducto(String id, String nombre, String vendedor, boolean visible) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setVendedor(vendedor);
        producto.setEsVisible(visible);
        producto.setCategoria("General");
        producto.setStock(5);
        producto.setPrecio(10.0);
        return producto;
    }
}
