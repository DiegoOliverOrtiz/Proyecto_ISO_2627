package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.FiltroProductosRequest;
import es.uclm.esibuy.backend.dto.ProductoCatalogoResponse;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoCatalogoServiceHU19Tests {
    private static final String EMAIL_CLIENTE = "cliente@example.com";
    private static final String ID_PRODUCTO = "producto-1";

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
    void combinaNombreCategoriaRangoPrecioDisponibilidadYDescuentoSegunCliente() {
        Producto matching = crearProducto(ID_PRODUCTO, "Teclado Gaming", "Informática", 4, 100.0, 10, 20, false);
        matching.setDescripcion("<b>mecánico</b>");
        matching.setVendedor("<script>tienda</script>");
        Producto noMatch = crearProducto("otro", "Ratón", "Informática", 10, 80.0, 0, 0, false);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(matching, noMatch));

        List<ProductoCatalogoResponse> productos = consultar(
                new FiltroProductosRequest("gaming", "informática", 79.99, 80.01, true));

        assertEquals(1, productos.size());
        assertEquals(ID_PRODUCTO, productos.get(0).id());
        assertEquals(80.0, productos.get(0).precio());
        assertEquals(100.0, productos.get(0).precioOriginal());
        assertEquals(20, productos.get(0).descuentoAplicado());
        assertEquals("<b>mecánico</b>", productos.get(0).descripcion());
        assertEquals("<script>tienda</script>", productos.get(0).vendedor());
    }

    @Test
    void filtraNombreComoTextoLiteralNoComoExpresionRegular() {
        Producto producto = crearProducto(ID_PRODUCTO, "Teclado.*", "Informática", 2, 25.0, 0, 0, false);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(producto));

        assertEquals(1, consultar(new FiltroProductosRequest(".*", null, null, null, null)).size());
        assertTrue(consultar(new FiltroProductosRequest("Teclado.*|Ratón", null, null, null, null)).isEmpty());
    }

    @Test
    void textoConOperadoresMongoNoModificaElSignificadoDeLosFiltros() {
        Producto producto = crearProducto(ID_PRODUCTO, "Producto real", "Informática", 1, 25.0, 0, 0, false);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(producto));

        assertTrue(consultar(new FiltroProductosRequest("{ \"$ne\": null }", null, null, null, null)).isEmpty());
        assertTrue(consultar(new FiltroProductosRequest(null, "$ne", null, null, null)).isEmpty());
    }

    @Test
    void filtraPorPrecioAplicableDisponibilidadYExcluyeNombresDesactivados() {
        Producto conStock = crearProducto(ID_PRODUCTO, "Disponible", "Informática", 1, 50.0, 10, 40, false);
        Producto agotado = crearProducto("agotado", "Agotado", "Hogar", 0, 50.0, 10, 40, false);
        Producto desactivado = crearProducto("inactivo", "Desactivado", "Hogar", 8, 50.0, 10, 40, false);
        desactivado.setEsVisible(false);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(conStock, agotado));

        List<ProductoCatalogoResponse> resultado =
                consultar(new FiltroProductosRequest(null, null, 44.99, 45.01, true));

        assertEquals(1, resultado.size());
        assertEquals(ID_PRODUCTO, resultado.get(0).id());
        assertEquals(45.0, resultado.get(0).precio());
        verify(productoDAO).findByEsVisibleTrue();
        verify(productoDAO, never()).findAll();
    }

    @Test
    void unClientePremiumUsaElDescuentoPremiumSiExisteYElNormalComoAlternativa() {
        Producto conPremium = crearProducto(ID_PRODUCTO, "Premium", "Informática", 3, 100.0, 10, 25, false);
        Producto sinPremium = crearProducto("normal", "Normal", "Informática", 3, 100.0, 10, 0, false);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(conPremium, sinPremium));

        List<ProductoCatalogoResponse> resultado = consultar(new FiltroProductosRequest(null, null, null, null, null));

        assertEquals(75.0, resultado.get(0).precio());
        assertEquals(25, resultado.get(0).descuentoAplicado());
        assertEquals(90.0, resultado.get(1).precio());
        assertEquals(10, resultado.get(1).descuentoAplicado());
    }

    @Test
    void clienteNormalVeProductoPremiumBloqueadoPeroSinSuDescripcion() {
        Producto premium = crearProducto(ID_PRODUCTO, "Solo Premium", "Informática", 5, 25.0, 0, 20, true);
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(premium));

        ProductoCatalogoResponse resultado =
                consultar(new FiltroProductosRequest(null, null, null, null, null)).get(0);

        assertTrue(resultado.soloPremium());
        assertFalse(resultado.puedeConsultarDetalle());
        assertNull(resultado.descripcion());
    }

    @Test
    void rechazaRangoDePrecioInvertido() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));

        assertThrows(
                IllegalArgumentException.class,
                () -> productoService.consultarProductosParaClientes(
                        EMAIL_CLIENTE, new FiltroProductosRequest(null, null, 100.0, 10.0, null)));
        verify(productoDAO, never()).findByEsVisibleTrue();
    }

    @Test
    void rechazaClienteInactivoAntesDeConsultarProductos() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> consultar(new FiltroProductosRequest(null, null, null, null, null)));
        verify(productoDAO, never()).findByEsVisibleTrue();
    }

    @Test
    void rechazaClienteInactivoAntesDeConsultarElDetalle() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, ID_PRODUCTO));
        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaClienteBloqueadoAntesDeConsultarProductos() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> consultar(new FiltroProductosRequest(null, null, null, null, null)));
        verify(productoDAO, never()).findByEsVisibleTrue();
    }

    @Test
    void noPermiteConsultarDetallePremiumDirectamenteAClienteNormal() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, true)));
        when(productoDAO.findById(ID_PRODUCTO))
                .thenReturn(Optional.of(crearProducto(ID_PRODUCTO, "Premium", "Informática", 5, 25.0, 0, 20, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, ID_PRODUCTO));
    }

    @Test
    void productoInexistenteYProductoDesactivadoDevuelvenNoEncontrado() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, true)));
        when(productoDAO.findById("missing")).thenReturn(Optional.empty());
        Producto desactivado = crearProducto(ID_PRODUCTO, "Desactivado", "Informática", 5, 25.0, 0, 0, false);
        desactivado.setEsVisible(false);
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(desactivado));

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, "missing"));
        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, ID_PRODUCTO));
    }

    @Test
    void detalleDevuelvePrecioFinalYVendedorComoTexto() {
        Producto producto = crearProducto(ID_PRODUCTO, "Detalle", "Informática", 4, 50.0, 10, 20, false);
        producto.setDescripcion("<img src=x onerror=alert(1)>");
        producto.setVendedor("<svg onload=alert(1)>");
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, true)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));

        ProductoCatalogoResponse detalle =
                productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, ID_PRODUCTO);

        assertEquals(40.0, detalle.precio());
        assertEquals(20, detalle.descuentoAplicado());
        assertEquals("<img src=x onerror=alert(1)>", detalle.descripcion());
        assertEquals("<svg onload=alert(1)>", detalle.vendedor());
        assertTrue(detalle.puedeConsultarDetalle());
    }

    private List<ProductoCatalogoResponse> consultar(FiltroProductosRequest filtros) {
        return productoService.consultarProductosParaClientes(EMAIL_CLIENTE, filtros);
    }

    private static Cliente crearCliente(boolean premium, boolean activado) {
        return crearCliente(premium, activado, false);
    }

    private static Cliente crearCliente(boolean premium, boolean activado, boolean bloqueado) {
        Cliente cliente = new Cliente();
        cliente.setEmail(EMAIL_CLIENTE);
        cliente.setEsPremium(premium);
        cliente.setActivada(activado);
        cliente.setBloqueada(bloqueado);
        return cliente;
    }

    private static Producto crearProducto(
            String id,
            String nombre,
            String categoria,
            int stock,
            double precio,
            int descuento,
            int descuentoPremium,
            boolean soloPremium) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setStock(stock);
        producto.setDescripcion("Descripción");
        producto.setPrecio(precio);
        producto.setDescuento(descuento);
        producto.setDescuentoPremium(descuentoPremium);
        producto.setSoloPremium(soloPremium);
        producto.setEsVisible(true);
        producto.setVendedor("Tienda de prueba");
        return producto;
    }
}
