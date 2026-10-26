package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
