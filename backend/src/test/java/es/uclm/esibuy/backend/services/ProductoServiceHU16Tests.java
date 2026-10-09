package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.CategoriaDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.exceptions.EstadoProductoException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.model.Vendedor;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductoServiceHU16Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String EMAIL_CLIENTE = "cliente@example.com";
    private static final String NOMBRE_COMERCIAL = "Tienda de prueba";
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
    void desactivaUnProductoPropioCambiandoSoloSuEstado() {
        Producto producto = crearProducto(true, NOMBRE_COMERCIAL);
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));

        productoService.desactivarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        ArgumentCaptor<Producto> guardado = ArgumentCaptor.forClass(Producto.class);
        verify(productoDAO).save(guardado.capture());
        assertFalse(guardado.getValue().isEsVisible());
        assertEquals(ID_PRODUCTO, guardado.getValue().getId());
        assertEquals("Teclado", guardado.getValue().getNombre());
        assertEquals(NOMBRE_COMERCIAL, guardado.getValue().getVendedor());
    }

    @Test
    void activaUnProductoPropioDesactivado() {
        Producto producto = crearProducto(false, NOMBRE_COMERCIAL);
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));

        productoService.activarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO).save(producto);
        assertTrue(producto.isEsVisible());
    }

    @Test
    void laDesactivacionEsLogicaYNuncaEliminaElProducto() {
        Producto producto = crearProducto(true, NOMBRE_COMERCIAL);
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));

        productoService.desactivarProducto(EMAIL_VENDEDOR, ID_PRODUCTO);

        verify(productoDAO, never()).delete(any());
        verify(productoDAO, never()).deleteById(any());
        verify(productoDAO, never()).deleteAll();
    }

    @Test
    void rechazaCambiarElEstadoDeUnProductoDeOtroVendedor() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto(true, "Otra tienda")));

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.desactivarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).save(any());
    }

    @Test
    void rechazaCambiarElEstadoDeUnProductoInexistente() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.empty());

        assertThrows(
                ProductoNoEncontradoException.class,
                () -> productoService.activarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verify(productoDAO, never()).save(any());
    }

    @Test
    void rechazaVendedoresNoActivadosSinTocarProductos() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.desactivarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaVendedoresBloqueadosSinTocarProductos() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.activarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaCuentasQueNoSonDeVendedor() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.activarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaActivarUnProductoQueYaEstaActivo() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto(true, NOMBRE_COMERCIAL)));

        EstadoProductoException excepcion = assertThrows(
                EstadoProductoException.class,
                () -> productoService.activarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        assertEquals("El producto ya está activo.", excepcion.getMessage());
        verify(productoDAO, never()).save(any());
    }

    @Test
    void rechazaDesactivarUnProductoQueYaEstaDesactivado() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(Optional.of(crearProducto(false, NOMBRE_COMERCIAL)));

        EstadoProductoException excepcion = assertThrows(
                EstadoProductoException.class,
                () -> productoService.desactivarProducto(EMAIL_VENDEDOR, ID_PRODUCTO));

        assertEquals("El producto ya está desactivado.", excepcion.getMessage());
        verify(productoDAO, never()).save(any());
    }

    @Test
    void elListadoDelVendedorIncluyeElIdYElEstadoDeCadaProducto() {
        when(vendedorDAO.findByEmail(EMAIL_VENDEDOR)).thenReturn(Optional.of(crearVendedor(true, false)));
        when(productoDAO.findByVendedor(NOMBRE_COMERCIAL))
                .thenReturn(List.of(crearProducto(false, NOMBRE_COMERCIAL)));

        List<ConsultarProductosResponse> resultado = productoService.consultarProductosPropios(EMAIL_VENDEDOR);

        assertEquals(ID_PRODUCTO, resultado.get(0).id());
        assertFalse(resultado.get(0).esVisible());
    }

    @Test
    void losClientesSoloRecibenProductosActivos() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, false)));
        when(productoDAO.findByEsVisibleTrue()).thenReturn(List.of(crearProducto(true, NOMBRE_COMERCIAL)));

        List<ConsultarProductosResponse> resultado = productoService.consultarProductosParaClientes(EMAIL_CLIENTE);

        assertEquals(1, resultado.size());
        assertEquals(ID_PRODUCTO, resultado.get(0).id());
        assertTrue(resultado.get(0).esVisible());
        verify(productoDAO).findByEsVisibleTrue();
        verify(productoDAO, never()).findAll();
    }

    @Test
    void rechazaClientesNoActivadosSinConsultarProductos() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(false, false)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosParaClientes(EMAIL_CLIENTE));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaClientesBloqueadosSinConsultarProductos() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente(true, true)));

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosParaClientes(EMAIL_CLIENTE));

        verifyNoInteractions(productoDAO);
    }

    @Test
    void rechazaCuentasQueNoSonDeCliente() {
        when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> productoService.consultarProductosParaClientes(EMAIL_CLIENTE));

        verifyNoInteractions(productoDAO);
    }

    private Vendedor crearVendedor(boolean activado, boolean bloqueado) {
        Vendedor vendedor = new Vendedor();
        vendedor.setEmail(EMAIL_VENDEDOR);
        vendedor.setNombreComercial(NOMBRE_COMERCIAL);
        vendedor.setActivada(activado);
        vendedor.setBloqueada(bloqueado);
        return vendedor;
    }

    private Cliente crearCliente(boolean activado, boolean bloqueado) {
        Cliente cliente = new Cliente();
        cliente.setEmail(EMAIL_CLIENTE);
        cliente.setActivada(activado);
        cliente.setBloqueada(bloqueado);
        return cliente;
    }

    private Producto crearProducto(boolean activo, String vendedor) {
        Producto producto = new Producto();
        producto.setId(ID_PRODUCTO);
        producto.setNombre("Teclado");
        producto.setCategoria("Informática");
        producto.setStock(4);
        producto.setPrecio(49.99);
        producto.setEsVisible(activo);
        producto.setVendedor(vendedor);
        return producto;
    }
}
