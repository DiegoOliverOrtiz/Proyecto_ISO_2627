package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.CrearProductoRequest;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validation;
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

    @Mock
    private ClienteDAO clienteDAO;

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(
                productoDAO, vendedorDAO, categoriaDAO, validator, productoImagenService, clienteDAO);
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

    @Test
    void rechazaCrearProductoConDescuentoMayorQueCien() {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Informática")).thenReturn(true);
        CrearProductoRequest request = new CrearProductoRequest(
                "Mouse",
                "Informática",
                "Mouse gaming",
                20.0,
                10,
                0,
                101);

        assertThrows(InvalidProductException.class, () -> productoService.crearProducto(EMAIL, request, null));
        verify(productoDAO, never()).save(any(Producto.class));
    }

    @Test
    void consultaSoloPorElNombreComercialDelVendedorAutenticado() {
        Vendedor vendedor = crearVendedor(true, false);
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(vendedor));
        when(productoDAO.findByVendedor(NOMBRE_COMERCIAL)).thenReturn(List.of(crearProducto()));

        var resultado = productoService.consultarProductosPropios(EMAIL);

        assertEquals(1, resultado.size());
        verify(productoDAO).findByVendedor(NOMBRE_COMERCIAL);
        verify(productoDAO, never()).findAll();
        verify(productoDAO, never()).findByEsVisibleTrue();
        verify(productoDAO, never()).findByCategoriaIgnoreCase(any());
    }

    @Test
    void unVendedorNoVeLosProductosDeOtroVendedor() {
        Vendedor otroVendedor = crearVendedor(true, false);
        otroVendedor.setEmail("otro@example.com");
        otroVendedor.setNombreComercial("Otra tienda");
        Producto productoAjeno = crearProducto();
        productoAjeno.setNombre("Producto de otra tienda");
        productoAjeno.setVendedor("Otra tienda");
        when(vendedorDAO.findByEmail("otro@example.com")).thenReturn(Optional.of(otroVendedor));
        when(productoDAO.findByVendedor("Otra tienda")).thenReturn(List.of(productoAjeno));

        var resultado = productoService.consultarProductosPropios("otro@example.com");

        assertEquals(1, resultado.size());
        assertEquals("Producto de otra tienda", resultado.get(0).nombre());
        verify(productoDAO).findByVendedor("Otra tienda");
        verify(productoDAO, never()).findByVendedor(NOMBRE_COMERCIAL);
    }

    @Test
    void elEmailConOperadoresDeMongoSeTrataComoLiteralYNoDaAcceso() {
        String emailMalicioso = "{\"$ne\": \"\"}";
        when(vendedorDAO.findByEmail(emailMalicioso)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosPropios(emailMalicioso));

        verifyNoInteractions(productoDAO);
    }

    // --- HU17: solo premium, descuento premium, categoría libre y stock inicial ---

    private Producto crearProductoConSolicitud(CrearProductoRequest request) {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase(request.categoria())).thenReturn(true);
        when(productoDAO.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        productoService.crearProducto(EMAIL, request, null);

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoDAO).save(captor.capture());
        return captor.getValue();
    }

    private static CrearProductoRequest solicitud(
            String categoria, int stock, int descuentoPremium, Boolean soloPremium) {
        return new CrearProductoRequest(
                "Lámpara", categoria, "Lámpara de mesa", true, 25.0, stock, 0, descuentoPremium, soloPremium);
    }

    @Test
    void creaProductoMarcadoComoSoloPremium() {
        Producto guardado = crearProductoConSolicitud(solicitud("Informática", 5, 20, true));

        assertTrue(guardado.isSoloPremium());
        assertEquals(20, guardado.getDescuentoPremium());
    }

    @Test
    void creaProductoSinMarcaSoloPremiumPorDefecto() {
        assertFalse(crearProductoConSolicitud(solicitud("Informática", 5, 0, null)).isSoloPremium());
    }

    @Test
    void creaProductoExplicitamenteNoSoloPremium() {
        assertFalse(crearProductoConSolicitud(solicitud("Informática", 5, 0, false)).isSoloPremium());
    }

    @Test
    void elConstructorSimpleCreaProductosNoSoloPremium() {
        CrearProductoRequest request =
                new CrearProductoRequest("Lámpara", "Informática", "Lámpara", 25.0, 5, 0, 0);

        assertFalse(crearProductoConSolicitud(request).isSoloPremium());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 50, 100 })
    void aceptaDescuentoPremiumEntreCeroYCien(int descuentoPremium) {
        assertEquals(
                descuentoPremium,
                crearProductoConSolicitud(solicitud("Informática", 5, descuentoPremium, true))
                        .getDescuentoPremium());
    }

    @ParameterizedTest
    @ValueSource(ints = { 101, 1000, -1 })
    void rechazaDescuentoPremiumFueraDeRango(int descuentoPremium) {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Informática")).thenReturn(true);

        assertThrows(
                InvalidProductException.class,
                () -> productoService.crearProducto(
                        EMAIL, solicitud("Informática", 5, descuentoPremium, true), null));
        verify(productoDAO, never()).save(any(Producto.class));
    }

    @Test
    void elDescuentoPremiumEsValidoSeaONoSoloPremium() {
        assertEquals(30, crearProductoConSolicitud(solicitud("Informática", 5, 30, false)).getDescuentoPremium());
    }

    @Test
    void aceptaCualquierCategoriaExistenteConIndependenciaDeLaDelVendedor() {
        // El vendedor se registró en otra categoría, pero el producto puede ser de cualquiera.
        Producto guardado = crearProductoConSolicitud(solicitud("Jardinería", 5, 0, false));

        assertEquals("Jardinería", guardado.getCategoria());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void rechazaCrearProductoConStockInicialCeroONegativo(int stock) {
        when(vendedorDAO.findByEmail(EMAIL)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(categoriaDAO.existsByNombreIgnoreCase("Informática")).thenReturn(true);

        assertThrows(
                InvalidProductException.class,
                () -> productoService.crearProducto(EMAIL, solicitud("Informática", stock, 0, false), null));
        verify(productoDAO, never()).save(any(Producto.class));
    }

    @Test
    void elProductoSeAsociaSiempreAlVendedorAutenticado() {
        // La petición no tiene campo de vendedor: solo puede asociarse al de la sesión.
        assertEquals(
                NOMBRE_COMERCIAL,
                crearProductoConSolicitud(solicitud("Informática", 5, 0, true)).getVendedor());
    }

    @Test
    void laValidacionDeLaPeticionRechazaStockCeroPrecioCeroYDescuentosInvalidos() {
        Validator real = Validation.buildDefaultValidatorFactory().getValidator();

        assertTrue(real.validate(solicitud("Informática", 5, 100, true)).isEmpty());
        assertFalse(real.validate(solicitud("Informática", 0, 0, true)).isEmpty());
        assertFalse(real.validate(solicitud("Informática", 5, 101, true)).isEmpty());
        assertFalse(real.validate(solicitud("Informática", 5, -1, true)).isEmpty());
        assertFalse(real.validate(new CrearProductoRequest("Lámpara", "Informática", "d", 0.0, 5, 0, 0)).isEmpty());
        assertFalse(real.validate(new CrearProductoRequest("", "Informática", "d", 10.0, 5, 0, 0)).isEmpty());
        assertFalse(real.validate(new CrearProductoRequest("Lámpara", "", "d", 10.0, 5, 0, 0)).isEmpty());
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
