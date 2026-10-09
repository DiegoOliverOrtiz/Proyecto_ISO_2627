package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTests {

    private static final String EMAIL = "vendedor@example.com";
    private static final String NOMBRE_COMERCIAL = "Tienda de prueba";

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

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(productoDAO, vendedorDAO, categoriaDAO, validator, productoImagenService);
        lenient().when(validator.validate(any())).thenReturn(Collections.emptySet());
    }

    @Test
    void devuelveSoloLosProductosAsociadosAlNombreComercialDelVendedor() {
        Vendedor vendedor = crearVendedor(true, false);
        Producto producto = crearProducto();
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(vendedor));
        when(productoDAO.findByVendedor(NOMBRE_COMERCIAL)).thenReturn(List.of(producto));

        var resultado = productoService.consultarProductosPropios(EMAIL);

        assertEquals(1, resultado.size());
        assertEquals("Teclado", resultado.get(0).nombre());
        assertEquals("Informática", resultado.get(0).categoria());
        assertEquals(4, resultado.get(0).stock());
        assertEquals("Descripción", resultado.get(0).descripcion());
        assertEquals(49.99, resultado.get(0).precio());
        assertEquals(10, resultado.get(0).descuento());
        assertEquals(15, resultado.get(0).descuentoPremium());
        assertEquals(true, resultado.get(0).esVisible());
        verify(vendedorDAO).findByEmail(EMAIL);
        verify(productoDAO).findByVendedor(NOMBRE_COMERCIAL);
    }

    @Test
    void devuelveListaVaciaCuandoElVendedorNoTieneProductos() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findByVendedor(NOMBRE_COMERCIAL)).thenReturn(List.of());

        assertEquals(List.of(), productoService.consultarProductosPropios(EMAIL));
    }

    @Test
    void rechazaVendedoresNoActivadosSinConsultarProductos() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosPropios(EMAIL));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaVendedoresBloqueadosSinConsultarProductos() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosPropios(EMAIL));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaLaConsultaSiLaCuentaYaNoEsDeUnVendedor() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosPropios(EMAIL));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void creaProductoAsignandoElVendedorAutenticado() {
        Vendedor vendedor = crearVendedor(true, false);
        CrearProductoRequest request = new CrearProductoRequest(
                "Teclado mecánico",
                "Informática",
                "Teclado gaming",
                89.99,
                12,
                5,
                10);
        Producto guardado = new Producto();
        guardado.setNombre("Teclado mecánico");
        guardado.setCategoria("Informática");
        guardado.setDescripcion("Teclado gaming");
        guardado.setPrecio(89.99);
        guardado.setStock(12);
        guardado.setDescuento(5);
        guardado.setDescuentoPremium(10);
        guardado.setEsVisible(true);
        guardado.setVendedor(NOMBRE_COMERCIAL);

        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(vendedor));
        when(categoriaDAO.existsByNombreIgnoreCase("Informática")).thenReturn(true);
        when(productoDAO.save(any(Producto.class))).thenReturn(guardado);

        var resultado = productoService.crearProducto(EMAIL, request, null);

        assertEquals("Teclado mecánico", resultado.nombre());
        assertEquals("Informática", resultado.categoria());
        assertEquals(89.99, resultado.precio());
        assertEquals(12, resultado.stock());
        assertEquals(5, resultado.descuento());
        assertEquals(10, resultado.descuentoPremium());
        verify(productoDAO).save(any(Producto.class));
    }

    @Test
    void rechazaCrearProductoCuandoElVendedorNoEstáActivo() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(false, false)));
        CrearProductoRequest request = new CrearProductoRequest(
                "Mouse",
                "Informática",
                "Mouse gaming",
                20.0,
                10,
                0,
                0);

        assertThrows(AccessDeniedException.class, () -> productoService.crearProducto(EMAIL, request, null));
    }

    @Test
    void rechazaCrearProductoConCategoriaNoExistente() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("No existe")).thenReturn(false);
        CrearProductoRequest request = new CrearProductoRequest(
                "Mouse",
                "No existe",
                "Mouse gaming",
                20.0,
                10,
                0,
                0);

        assertThrows(InvalidProductException.class, () -> productoService.crearProducto(EMAIL, request, null));
    }

    @Test
    void rechazaCrearProductoConPrecioInvalido() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Informática")).thenReturn(true);
        CrearProductoRequest request = new CrearProductoRequest(
                "Mouse",
                "Informática",
                "Mouse gaming",
                0.0,
                10,
                0,
                0);

        assertThrows(InvalidProductException.class, () -> productoService.crearProducto(EMAIL, request, null));
    }

    private Vendedor crearVendedor(boolean activado, boolean bloqueado) {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail(EMAIL);
        vendedor.setNombreComercial(NOMBRE_COMERCIAL);
        vendedor.setActivada(activado);
        vendedor.setBloqueada(bloqueado);
        return vendedor;
    }

    private Producto crearProducto() {
        Producto producto = new Producto();
        producto.setNombre("Teclado");
        producto.setCategoria("Informática");
        producto.setStock(4);
        producto.setDescripcion("Descripción");
        producto.setPrecio(49.99);
        producto.setDescuento(10);
        producto.setDescuentoPremium(15);
        producto.setEsVisible(true);
        producto.setVendedor(NOMBRE_COMERCIAL);
        return producto;
    }
}
