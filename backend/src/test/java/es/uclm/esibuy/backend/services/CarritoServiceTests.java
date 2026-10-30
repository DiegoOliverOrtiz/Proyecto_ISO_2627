package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.model.Carrito;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTests {
    @Mock private CarritoDAO carritoDAO;
    @Mock private ClienteDAO clienteDAO;
    @Mock private ProductoDAO productoDAO;
    private CarritoService servicio;
    private Cliente cliente;
    private Producto producto;

    @BeforeEach
    void setUp() {
        servicio = new CarritoService(carritoDAO, clienteDAO, productoDAO);
        cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(true);
        producto = new Producto();
        producto.setId("producto-1");
        producto.setStock(10);
        producto.setPrecio(10.0);
        producto.setEsVisible(true);
        autenticar("ROLE_CLIENTE");
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cliente@example.com", null,
                        List.of(new SimpleGrantedAuthority(rol))));
    }

    private void prepararCliente() {
        when(clienteDAO.findByEmail("cliente@example.com")).thenReturn(Optional.of(cliente));
    }

    private void prepararProducto() {
        prepararCliente();
        when(productoDAO.findById("producto-1")).thenReturn(Optional.of(producto));
    }

    private void prepararCarrito(Carrito carrito) {
        prepararProducto();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.ofNullable(carrito));
    }

    @Test
    void creaCarritoDelClienteAutenticadoSinDescontarStock() {
        prepararCarrito(null);
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Carrito resultado = servicio.anadirProducto("producto-1", 3);
        assertEquals("cliente-a", resultado.getId());
        assertEquals(3, resultado.cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(productoDAO, never()).save(any());
        verify(carritoDAO, never()).findById("cliente-b");
    }

    @Test
    void productoRepetidoSumaSinCrearOtraLinea() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 3));
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Carrito resultado = servicio.anadirProducto("producto-1", 2);
        assertEquals(5, resultado.cantidadDe("producto-1"));
        assertEquals(1, resultado.getProductos().size());
        assertEquals(10, producto.getStock());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rechazaCantidadNoPositiva(int cantidad) {
        prepararCliente();
        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", cantidad));
        verifyNoInteractions(productoDAO, carritoDAO);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 11, Integer.MAX_VALUE})
    void rechazaStockAcumuladoSinCambiarCarrito(int cantidad) {
        Carrito anterior = new Carrito("cliente-a").conCantidad("producto-1", 5);
        prepararCarrito(anterior);
        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", cantidad));
        assertEquals(5, anterior.cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(carritoDAO, never()).save(any());
    }

    @Test
    void rechazaProductoInexistente() {
        prepararCliente();
        when(productoDAO.findById("producto-1")).thenReturn(Optional.empty());
        assertThrows(ProductoNoEncontradoException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(carritoDAO);
    }

    @Test
    void rechazaProductoOculto() {
        prepararProducto();
        producto.setEsVisible(false);
        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(carritoDAO);
    }

    @Test
    void rechazaProductoAgotado() {
        prepararCarrito(null);
        producto.setStock(0);
        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", 1));
        verify(carritoDAO, never()).save(any());
    }

    @Test
    void clienteNormalNoPuedeAnadirPremium() {
        prepararProducto();
        producto.setSoloPremium(true);
        assertThrows(AccessDeniedException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(carritoDAO);
    }

    @Test
    void clientePremiumPuedeAnadirPremiumHastaLimiteStock() {
        prepararCarrito(null);
        cliente.setEsPremium(true);
        producto.setSoloPremium(true);
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(10, servicio.anadirProducto("producto-1", 10).cantidadDe("producto-1"));
    }

    @Test
    void rechazaSesionAusente() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(clienteDAO, productoDAO, carritoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_VENDEDOR", "ROLE_ANONYMOUS"})
    void rechazaOtrosRoles(String rol) {
        autenticar(rol);
        assertThrows(AccessDeniedException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(clienteDAO, productoDAO, carritoDAO);
    }

    @Test
    void rechazaClienteBloqueadoOInactivo() {
        prepararCliente();
        cliente.setBloqueada(true);
        assertThrows(AccessDeniedException.class, () -> servicio.anadirProducto("producto-1", 1));
        cliente.setBloqueada(false);
        cliente.setActivada(false);
        assertThrows(AccessDeniedException.class, () -> servicio.anadirProducto("producto-1", 1));
        verifyNoInteractions(productoDAO, carritoDAO);
    }

    @Test
    void reintentaConflictoYRevalidaCantidadActual() {
        prepararProducto();
        when(carritoDAO.findById("cliente-a")).thenReturn(
                Optional.of(new Carrito("cliente-a").conCantidad("producto-1", 3)),
                Optional.of(new Carrito("cliente-a").conCantidad("producto-1", 9)));
        when(carritoDAO.save(any())).thenThrow(new OptimisticLockingFailureException("conflicto"));
        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", 2));
        verify(carritoDAO).save(any());
        verify(productoDAO, times(2)).findById("producto-1");
    }

    @Test
    void reintentaCreacionSimultanea() {
        prepararProducto();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.empty(),
                Optional.of(new Carrito("cliente-a").conCantidad("producto-1", 2)));
        when(carritoDAO.save(any())).thenThrow(new DuplicateKeyException("duplicado"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(5, servicio.anadirProducto("producto-1", 3).cantidadDe("producto-1"));
    }

    @Test
    void limitaReintentos() {
        prepararCarrito(null);
        when(carritoDAO.save(any())).thenThrow(new OptimisticLockingFailureException("conflicto"));
        assertThrows(OptimisticLockingFailureException.class, () -> servicio.anadirProducto("producto-1", 1));
        verify(carritoDAO, times(3)).save(any());
    }

    @Test
    void falloPersistenciaNoMutaCarritoLeido() {
        Carrito anterior = new Carrito("cliente-a").conCantidad("producto-1", 3);
        prepararCarrito(anterior);
        when(carritoDAO.save(any())).thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(DataAccessResourceFailureException.class, () -> servicio.anadirProducto("producto-1", 2));
        assertEquals(3, anterior.cantidadDe("producto-1"));
        verify(carritoDAO).save(any());
    }
    @Test
    void calculaImporteConDescuentoGeneralYPremiumSinAcumularlos() {
        prepararCarrito(null);
        producto.setPrecio(19.99);
        producto.setDescuento(10);
        producto.setDescuentoPremium(20);
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(new java.math.BigDecimal("53.97"),
                servicio.anadirProductoConImporte("producto-1", 3).importe());
        cliente.setEsPremium(true);
        assertEquals(new java.math.BigDecimal("47.97"),
                servicio.anadirProductoConImporte("producto-1", 3).importe());
        producto.setDescuentoPremium(0);
        assertEquals(new java.math.BigDecimal("53.97"),
                servicio.anadirProductoConImporte("producto-1", 3).importe());
    }

    @Test
    void recalculaTodasLasLineasConPreciosActuales() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("otro", 2));
        Producto otro = new Producto();
        otro.setId("otro");
        otro.setEsVisible(true);
        otro.setStock(4);
        otro.setPrecio(3.50);
        when(productoDAO.findById("otro")).thenReturn(Optional.of(otro));
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(new java.math.BigDecimal("37.00"),
                servicio.anadirProductoConImporte("producto-1", 3).importe());
    }

    @Test
    void lineaAnteriorNoDisponibleImpideGuardar() {
        Carrito anterior = new Carrito("cliente-a").conCantidad("otro", 2);
        prepararCarrito(anterior);
        when(productoDAO.findById("otro")).thenReturn(Optional.empty());
        assertThrows(ProductoNoEncontradoException.class,
                () -> servicio.anadirProductoConImporte("producto-1", 3));
        verify(carritoDAO, never()).save(any());
        assertEquals(0, anterior.cantidadDe("producto-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"oculto", "premium", "stock", "precio"})
    void rechazaLineaAnteriorQueYaNoPuedeAdquirirse(String motivo) {
        Carrito anterior = new Carrito("cliente-a").conCantidad("otro", 2);
        prepararCarrito(anterior);
        Producto otro = new Producto();
        otro.setId("otro");
        otro.setEsVisible(!motivo.equals("oculto"));
        otro.setSoloPremium(motivo.equals("premium"));
        otro.setStock(motivo.equals("stock") ? 1 : 10);
        otro.setPrecio(motivo.equals("precio") ? null : 5.0);
        when(productoDAO.findById("otro")).thenReturn(Optional.of(otro));
        if (motivo.equals("premium")) {
            assertThrows(AccessDeniedException.class,
                    () -> servicio.anadirProductoConImporte("producto-1", 3));
        } else {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.anadirProductoConImporte("producto-1", 3));
        }
        verify(carritoDAO, never()).save(any());
        assertEquals(0, anterior.cantidadDe("producto-1"));
    }

    @Test
    void altasSucesivasConservanStockYRechazoConservaUltimoCarritoGuardado() {
        prepararProducto();
        AtomicReference<Carrito> persistido = new AtomicReference<>();
        when(carritoDAO.findById("cliente-a"))
                .thenAnswer(invocation -> Optional.ofNullable(persistido.get()));
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> {
            Carrito carrito = invocation.getArgument(0);
            persistido.set(carrito);
            return carrito;
        });

        assertEquals(3, servicio.anadirProducto("producto-1", 3).cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        assertEquals(5, servicio.anadirProducto("producto-1", 2).cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        Carrito anterior = persistido.get();

        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", 6));

        assertSame(anterior, persistido.get());
        assertEquals(5, persistido.get().cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(carritoDAO, times(2)).save(any());
        verify(productoDAO, times(3)).findById("producto-1");
        verifyNoMoreInteractions(productoDAO);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void vuelveAConsultarStockSiCambiaEntreOperaciones(int stockActual) {
        prepararProducto();
        AtomicReference<Carrito> persistido = new AtomicReference<>();
        when(carritoDAO.findById("cliente-a"))
                .thenAnswer(invocation -> Optional.ofNullable(persistido.get()));
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> {
            Carrito carrito = invocation.getArgument(0);
            persistido.set(carrito);
            return carrito;
        });
        servicio.anadirProducto("producto-1", 3);
        Carrito anterior = persistido.get();

        // Simula un cambio externo del stock después de la primera operación.
        Producto actualizado = new Producto();
        actualizado.setId("producto-1");
        actualizado.setStock(stockActual);
        actualizado.setPrecio(10.0);
        actualizado.setEsVisible(true);
        when(productoDAO.findById("producto-1")).thenReturn(Optional.of(actualizado));

        assertThrows(IllegalArgumentException.class, () -> servicio.anadirProducto("producto-1", 2));

        assertSame(anterior, persistido.get());
        assertEquals(3, persistido.get().cantidadDe("producto-1"));
        assertEquals(stockActual, actualizado.getStock());
        assertEquals(10, producto.getStock());
        verify(carritoDAO).save(any());
        verify(productoDAO, times(2)).findById("producto-1");
        verifyNoMoreInteractions(productoDAO);
    }

    @Test
    void carritoAbandonadoNoReservaStockParaOtroCliente() {
        prepararCarrito(null);
        when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Carrito carritoA = servicio.anadirProducto("producto-1", 10);

        Cliente otroCliente = new Cliente();
        otroCliente.setId("cliente-b");
        otroCliente.setActivada(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        when(clienteDAO.findByEmail("otro@example.com")).thenReturn(Optional.of(otroCliente));
        when(carritoDAO.findById("cliente-b")).thenReturn(Optional.empty());

        Carrito carritoB = servicio.anadirProducto("producto-1", 10);

        assertEquals("cliente-a", carritoA.getId());
        assertEquals("cliente-b", carritoB.getId());
        assertEquals(10, carritoA.cantidadDe("producto-1"));
        assertEquals(10, carritoB.cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(productoDAO, times(2)).findById("producto-1");
        verifyNoMoreInteractions(productoDAO);
    }
}
