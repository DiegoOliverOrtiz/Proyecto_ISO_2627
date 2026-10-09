package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.ModificarProductoRequest;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoServiceHU18Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String NOMBRE_COMERCIAL = "Tienda de prueba";
    private static final String ID_PRODUCTO = "producto-1";
    private static final String IMAGEN_ACTUAL = "/product-images/actual.png";
    private static final String IMAGEN_NUEVA = "/product-images/nueva.png";
    private static final ModificarProductoRequest REQUEST_VALIDO = new ModificarProductoRequest(
            "Teclado mecánico", "Periféricos", "Retroiluminado", 59.99, 10, 15, 20);

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

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(
                productoDAO, vendedorDAO, categoriaDAO, validator, productoImagenService, clienteDAO);
    }

    @Test
    void modificaLosCamposPermitidosDeUnProductoPropio() {
        prepararModificacionValida(crearProducto(NOMBRE_COMERCIAL));

        ConsultarProductosResponse response = productoService.modificarProducto(
                EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null);

        Producto guardado = productoGuardado();
        assertEquals(ID_PRODUCTO, guardado.getId());
        assertEquals("Teclado mecánico", guardado.getNombre());
        assertEquals("Periféricos", guardado.getCategoria());
        assertEquals("Retroiluminado", guardado.getDescripcion());
        assertEquals(59.99, guardado.getPrecio());
        assertEquals(10, guardado.getStock());
        assertEquals(15, guardado.getDescuento());
        assertEquals(20, guardado.getDescuentoPremium());
        assertEquals(ID_PRODUCTO, response.id());
        assertEquals("Teclado mecánico", response.nombre());
    }

    @Test
    void noModificaElPropietarioNiLaVisibilidad() {
        Producto producto = crearProducto(NOMBRE_COMERCIAL);
        producto.setEsVisible(false);
        prepararModificacionValida(producto);

        productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null);

        Producto guardado = productoGuardado();
        assertEquals(NOMBRE_COMERCIAL, guardado.getVendedor());
        assertFalse(guardado.isEsVisible());
    }

    @Test
    void normalizaElNombreLaCategoriaYLaDescripcion() {
        prepararModificacionValida(crearProducto(NOMBRE_COMERCIAL));
        ModificarProductoRequest request = new ModificarProductoRequest(
                "  Teclado mecánico  ", "  Periféricos ", "  Retroiluminado ", 59.99, 10, 15, 20);

        productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, request, null);

        Producto guardado = productoGuardado();
        assertEquals("Teclado mecánico", guardado.getNombre());
        assertEquals("Periféricos", guardado.getCategoria());
        assertEquals("Retroiluminado", guardado.getDescripcion());
    }

    @Test
    void aceptaStockCeroYDescuentosEnLosLimites() {
        prepararModificacionValida(crearProducto(NOMBRE_COMERCIAL));
        ModificarProductoRequest request = new ModificarProductoRequest(
                "Teclado", "Periféricos", null, 0.01, 0, 0, 100);

        productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, request, null);

        Producto guardado = productoGuardado();
        assertEquals(0, guardado.getStock());
        assertEquals(100, guardado.getDescuentoPremium());
    }

    @Test
    void conservaLaImagenActualSiNoSeEnviaOtra() {
        prepararModificacionValida(crearProducto(NOMBRE_COMERCIAL));

        productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null);

        assertEquals(IMAGEN_ACTUAL, productoGuardado().getImagen());
        verify(productoImagenService, never()).eliminar(any());
    }

    @Test
    void sustituyeLaImagenYEliminaLaAnterior() {
        MockMultipartFile imagen = crearImagen();
        prepararModificacionValida(crearProducto(NOMBRE_COMERCIAL));
        when(productoImagenService.guardar(imagen)).thenReturn(IMAGEN_NUEVA);

        ConsultarProductosResponse response = productoService.modificarProducto(
                EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, imagen);

        assertEquals(IMAGEN_NUEVA, productoGuardado().getImagen());
        assertEquals(IMAGEN_NUEVA, response.imagen());
        verify(productoImagenService).eliminar(IMAGEN_ACTUAL);
    }

    @Test
    void eliminaLaImagenNuevaYConservaLaAnteriorSiFallaElGuardado() {
        MockMultipartFile imagen = crearImagen();
        RuntimeException errorGuardado = new IllegalStateException("Fallo de base de datos");
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Periféricos")).thenReturn(true);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto(NOMBRE_COMERCIAL)));
        when(productoImagenService.guardar(imagen)).thenReturn(IMAGEN_NUEVA);
        when(productoDAO.save(any(Producto.class))).thenThrow(errorGuardado);

        RuntimeException lanzada = assertThrows(
                RuntimeException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, imagen));

        assertSame(errorGuardado, lanzada);
        verify(productoImagenService).eliminar(IMAGEN_NUEVA);
        verify(productoImagenService, never()).eliminar(IMAGEN_ACTUAL);
    }

    @Test
    void rechazaModificarUnProductoDeOtroVendedor() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Periféricos")).thenReturn(true);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto("Otra tienda")));

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null));

        verify(productoDAO, never()).save(any());
        verifyNoInteractions(productoImagenService);
    }

    @Test
    void rechazaModificarUnProductoInexistente() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Periféricos")).thenReturn(true);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.empty());

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null));

        verify(productoDAO, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "true, true", "false, true" })
    void rechazaVendedoresNoActivos(boolean activado, boolean bloqueado) {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR))
                .thenReturn(Optional.of(crearVendedor(activado, bloqueado)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null));

        verifyNoInteractions(productoDAO, productoImagenService);
    }

    @Test
    void rechazaCuentasQueNoSonDeVendedor() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, REQUEST_VALIDO, null));

        verifyNoInteractions(productoDAO, productoImagenService);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0, -1, -0.01 })
    void rechazaPreciosQueNoSonMayoresQueCero(double precio) {
        assertDatosInvalidos(new ModificarProductoRequest("Teclado", "Periféricos", null, precio, 1, 0, 0));
    }

    @Test
    void rechazaPrecioNulo() {
        assertDatosInvalidos(new ModificarProductoRequest("Teclado", "Periféricos", null, null, 1, 0, 0));
    }

    @Test
    void rechazaStockNegativo() {
        assertDatosInvalidos(new ModificarProductoRequest("Teclado", "Periféricos", null, 10.0, -1, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({ "-1, 0", "101, 0", "0, -1", "0, 101" })
    void rechazaDescuentosFueraDeRango(int descuento, int descuentoPremium) {
        assertDatosInvalidos(new ModificarProductoRequest(
                "Teclado", "Periféricos", null, 10.0, 1, descuento, descuentoPremium));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void rechazaNombreVacio(String nombre) {
        assertDatosInvalidos(new ModificarProductoRequest(nombre, "Periféricos", null, 10.0, 1, 0, 0));
    }

    @Test
    void rechazaPeticionNula() {
        assertDatosInvalidos(null);
    }

    @Test
    void rechazaCategoriaVacia() {
        assertDatosInvalidos(new ModificarProductoRequest("Teclado", "  ", null, 10.0, 1, 0, 0));
    }

    @Test
    void rechazaCategoriaInexistente() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Inventada")).thenReturn(false);
        ModificarProductoRequest request = new ModificarProductoRequest(
                "Teclado", "Inventada", null, 10.0, 1, 0, 0);

        assertThrows(
                InvalidProductException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, request, null));

        verifyNoInteractions(productoDAO, productoImagenService);
    }

    private void assertDatosInvalidos(ModificarProductoRequest request) {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        // La categoría existe: así el rechazo se debe al dato inválido que prueba cada caso.
        lenient().when(categoriaDAO.existsByNombreIgnoreCase("Periféricos")).thenReturn(true);

        assertThrows(
                InvalidProductException.class,
                () -> productoService.modificarProducto(EMAIL_VENDEDOR, ID_PRODUCTO, request, null));

        verifyNoInteractions(productoDAO, productoImagenService);
    }

    private void prepararModificacionValida(Producto producto) {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Periféricos")).thenReturn(true);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        when(productoDAO.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private Producto productoGuardado() {
        ArgumentCaptor<Producto> guardado = ArgumentCaptor.forClass(Producto.class);
        verify(productoDAO).save(guardado.capture());
        return guardado.getValue();
    }

    private MockMultipartFile crearImagen() {
        return new MockMultipartFile("imagen", "teclado.png", "image/png", new byte[] { 1, 2, 3 });
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
        producto.setImagen(IMAGEN_ACTUAL);
        return producto;
    }
}
