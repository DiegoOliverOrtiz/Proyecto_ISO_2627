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

    @Test
    void consultaCarritoInexistenteSinPersistir() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.empty());
        var respuesta = servicio.consultarCarrito();
        assertTrue(respuesta.productos().isEmpty());
        assertEquals(new java.math.BigDecimal("0.00"), respuesta.importe());
        verify(carritoDAO, never()).save(any());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void consultaConDescuentoYPropietarioAutenticado() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 2));
        producto.setNombre("Producto A");
        producto.setDescuento(20);
        var respuesta = servicio.consultarCarrito();
        assertEquals(new java.math.BigDecimal("16.00"), respuesta.importe());
        assertEquals("Producto A", respuesta.productos().getFirst().nombre());
        assertTrue(respuesta.productos().getFirst().disponible());
        verify(carritoDAO, never()).findById("cliente-b");
        verify(carritoDAO, never()).save(any());
        verify(productoDAO, never()).save(any());
    }

    @Test
    void sustituyeCantidadSinSumarNiCambiarStock() {
        Carrito anterior = new Carrito("cliente-a").conCantidad("producto-1", 3);
        prepararCarrito(anterior);
        var respuesta = servicio.modificarCantidad("producto-1", 5);
        assertEquals(5, respuesta.productos().getFirst().cantidad());
        assertEquals(new java.math.BigDecimal("50.00"), respuesta.importe());
        assertEquals(3, anterior.cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(carritoDAO).save(argThat(c -> c.cantidadDe("producto-1") == 5));
        verify(productoDAO).findById("producto-1");
        verifyNoMoreInteractions(productoDAO);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void modificacionRechazaCantidadesInvalidas(int cantidad) {
        prepararCliente();
        assertThrows(IllegalArgumentException.class,
                () -> servicio.modificarCantidad("producto-1", cantidad));
        verifyNoInteractions(carritoDAO, productoDAO);
    }

    @ParameterizedTest
    @ValueSource(ints = {11, Integer.MAX_VALUE})
    void modificacionRechazaExcesoSinGuardar(int cantidad) {
        Carrito anterior = new Carrito("cliente-a").conCantidad("producto-1", 3);
        prepararCarrito(anterior);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.modificarCantidad("producto-1", cantidad));
        assertEquals(3, anterior.cantidadDe("producto-1"));
        assertEquals(10, producto.getStock());
        verify(carritoDAO, never()).save(any());
        verify(productoDAO, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eliminado", "oculto", "agotado", "premium", "stock", "precio"})
    void consultaConservaLineasNoDisponiblesSinImporte(String motivo) {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(
                new Carrito("cliente-a").conCantidad("producto-1", 3)));
        if (motivo.equals("oculto")) producto.setEsVisible(false);
        if (motivo.equals("agotado")) producto.setStock(0);
        if (motivo.equals("premium")) producto.setSoloPremium(true);
        if (motivo.equals("stock")) producto.setStock(2);
        if (motivo.equals("precio")) producto.setPrecio(Double.NaN);
        when(productoDAO.findById("producto-1")).thenReturn(
                motivo.equals("eliminado") ? Optional.empty() : Optional.of(producto));
        var respuesta = servicio.consultarCarrito();
        var linea = respuesta.productos().getFirst();
        assertFalse(linea.disponible());
        assertNotNull(linea.aviso());
        assertNull(linea.precioUnitario());
        assertNull(linea.subtotal());
        assertEquals(3, linea.cantidad());
        assertEquals(new java.math.BigDecimal("0.00"), respuesta.importe());
        verify(carritoDAO, never()).save(any());
    }

    @Test
    void corrigeStockReducidoAunqueOtraLineaEsteEliminada() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 8).conCantidad("borrado", 1));
        producto.setStock(4);
        when(productoDAO.findById("borrado")).thenReturn(Optional.empty());
        var respuesta = servicio.modificarCantidad("producto-1", 4);
        assertEquals(new java.math.BigDecimal("40.00"), respuesta.importe());
        assertEquals(2, respuesta.productos().size());
        assertEquals(4, producto.getStock());
        verify(productoDAO, never()).save(any());
    }

    @Test
    void eliminaProductoInexistenteEnCatalogoSinBloquearOtrasLineas() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 2).conCantidad("borrado", 1));
        var respuesta = servicio.eliminarProducto("borrado");
        assertEquals(1, respuesta.productos().size());
        assertEquals(new java.math.BigDecimal("20.00"), respuesta.importe());
        verify(carritoDAO).save(argThat(c -> c.cantidadDe("borrado") == 0 && c.cantidadDe("producto-1") == 2));
        assertEquals(10, producto.getStock());
        verifyNoMoreInteractions(productoDAO);
    }

    @Test
    void eliminarUltimaLineaMantieneDocumentoVacio() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(
                new Carrito("cliente-a").conCantidad("producto-1", 1)));
        var respuesta = servicio.eliminarProducto("producto-1");
        assertTrue(respuesta.productos().isEmpty());
        assertEquals(new java.math.BigDecimal("0.00"), respuesta.importe());
        verify(carritoDAO).save(argThat(c -> c.getId().equals("cliente-a") && c.getProductos().isEmpty()));
        verifyNoInteractions(productoDAO);
    }

    @Test
    void noPuedeModificarNiEliminarProductoSoloPresenteEnCarritoAjeno() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(new Carrito("cliente-a")));
        assertThrows(ProductoNoEncontradoException.class, () -> servicio.modificarCantidad("ajeno", 1));
        assertThrows(ProductoNoEncontradoException.class, () -> servicio.eliminarProducto("ajeno"));
        verify(carritoDAO, never()).findById("cliente-b");
        verify(carritoDAO, never()).save(any());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void nuevasOperacionesExigenClienteAutenticado() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> servicio.consultarCarrito());
        assertThrows(AccessDeniedException.class, () -> servicio.modificarCantidad("producto-1", 1));
        assertThrows(AccessDeniedException.class, () -> servicio.eliminarProducto("producto-1"));
        verifyNoInteractions(clienteDAO, carritoDAO, productoDAO);
    }

    @Test
    void modificacionReintentaSobreCarritoReciente() {
        prepararProducto();
        when(carritoDAO.findById("cliente-a")).thenReturn(
                Optional.of(new Carrito("cliente-a").conCantidad("producto-1", 2)),
                Optional.of(new Carrito("cliente-a").conCantidad("producto-1", 3)));
        when(carritoDAO.save(any())).thenThrow(new OptimisticLockingFailureException("conflicto"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(5, servicio.modificarCantidad("producto-1", 5).productos().getFirst().cantidad());
        verify(carritoDAO, times(2)).save(argThat(c -> c.cantidadDe("producto-1") == 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"oculto", "agotado", "precio"})
    void noModificaLineaNoDisponible(String motivo) {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 2));
        if (motivo.equals("oculto")) producto.setEsVisible(false);
        if (motivo.equals("agotado")) producto.setStock(0);
        if (motivo.equals("precio")) producto.setPrecio(null);
        assertThrows(IllegalArgumentException.class, () -> servicio.modificarCantidad("producto-1", 1));
        verify(carritoDAO, never()).save(any());
        verify(productoDAO, never()).save(any());
    }

    @Test
    void noModificaPremiumTrasPerderPermiso() {
        prepararCarrito(new Carrito("cliente-a").conCantidad("producto-1", 2));
        producto.setSoloPremium(true);
        assertThrows(AccessDeniedException.class, () -> servicio.modificarCantidad("producto-1", 1));
        verify(carritoDAO, never()).save(any());
    }

    @Test
    void eliminarNoMutaOriginalSiFallaPersistencia() {
        prepararCliente();
        Carrito original = new Carrito("cliente-a").conCantidad("producto-1", 2);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(original));
        when(carritoDAO.save(any())).thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(DataAccessResourceFailureException.class, () -> servicio.eliminarProducto("producto-1"));
        assertEquals(2, original.cantidadDe("producto-1"));
        verifyNoInteractions(productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "premium", "premiumSinDescuento"})
    void modificaYEliminaRecalculandoVariasLineasConDescuentosActuales(String tipo) {
        prepararCliente();
        cliente.setEsPremium(!tipo.equals("normal"));
        producto.setPrecio(10.05);
        producto.setDescuento(10);
        producto.setDescuentoPremium(tipo.equals("premiumSinDescuento") ? 0 : 20);
        Producto segundo = new Producto();
        segundo.setId("producto-2");
        segundo.setPrecio(7.50);
        segundo.setStock(8);
        segundo.setEsVisible(true);
        when(productoDAO.findById("producto-1")).thenReturn(Optional.of(producto));
        when(productoDAO.findById("producto-2")).thenReturn(Optional.of(segundo));
        AtomicReference<Carrito> guardado = new AtomicReference<>(new Carrito("cliente-a")
                .conCantidad("producto-1", 2).conCantidad("producto-2", 1));
        when(carritoDAO.findById("cliente-a")).thenAnswer(invocation -> Optional.of(guardado.get()));
        when(carritoDAO.save(any())).thenAnswer(invocation -> {
            guardado.set(invocation.getArgument(0));
            return guardado.get();
        });

        var consulta = servicio.consultarCarrito();
        String unitario = tipo.equals("premium") ? "8.04" : "9.05";
        String inicial = tipo.equals("premium") ? "23.58" : "25.60";
        assertEquals(new java.math.BigDecimal(inicial), consulta.importe());
        // Cambian externamente precio y stock antes de modificar el carrito.
        segundo.setPrecio(8.00);
        producto.setStock(5);
        var modificado = servicio.modificarCantidad("producto-1", 5);
        var primera = modificado.productos().stream().filter(l -> l.productoId().equals("producto-1"))
                .findFirst().orElseThrow();
        assertEquals(new java.math.BigDecimal(unitario), primera.precioUnitario());
        assertEquals(new java.math.BigDecimal(unitario).multiply(java.math.BigDecimal.valueOf(5)), primera.subtotal());
        assertEquals(tipo.equals("premium") ? new java.math.BigDecimal("48.20")
                : new java.math.BigDecimal("53.25"), modificado.importe());
        assertEquals(1, guardado.get().cantidadDe("producto-2"));

        var eliminado = servicio.eliminarProducto("producto-1");
        assertEquals(1, eliminado.productos().size());
        assertEquals("producto-2", eliminado.productos().getFirst().productoId());
        assertEquals(new java.math.BigDecimal("8.00"), eliminado.importe());
        assertEquals(0, guardado.get().cantidadDe("producto-1"));
        assertEquals(5, producto.getStock());
        assertEquals(8, segundo.getStock());
        verify(productoDAO, never()).save(any());
        verify(productoDAO, never()).delete(any());
    }

    @Test
    void eliminarLineaInexistenteNoCambiaLasDemas() {
        prepararCliente();
        Carrito original = new Carrito("cliente-a").conCantidad("producto-1", 2);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(original));
        assertThrows(ProductoNoEncontradoException.class, () -> servicio.eliminarProducto("inexistente"));
        assertEquals(2, original.cantidadDe("producto-1"));
        verify(carritoDAO, never()).save(any());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void modificacionRevalidaStockReducidoTrasConflicto() {
        prepararCliente();
        Carrito original = new Carrito("cliente-a").conCantidad("producto-1", 2);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(original));
        when(productoDAO.findById("producto-1")).thenReturn(Optional.of(producto));
        when(carritoDAO.save(any())).thenAnswer(invocation -> {
            producto.setStock(3);
            throw new OptimisticLockingFailureException("conflicto");
        });
        assertThrows(IllegalArgumentException.class, () -> servicio.modificarCantidad("producto-1", 5));
        assertEquals(2, original.cantidadDe("producto-1"));
        assertEquals(3, producto.getStock());
        verify(carritoDAO).save(any());
        verify(productoDAO, times(2)).findById("producto-1");
        verifyNoMoreInteractions(productoDAO);
    }

    @Test
    void descartaTodasLasLineasSoloDelPropietarioSinTocarProductos() {
        prepararCliente();
        Carrito original = new Carrito("cliente-a").conCantidad("producto-1", 3).conCantidad("eliminado", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(original, "version", 7L);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(original));
        var respuesta = servicio.descartarCarrito();
        assertTrue(respuesta.productos().isEmpty());
        assertEquals(new java.math.BigDecimal("0.00"), respuesta.importe());
        verify(carritoDAO).save(argThat(c -> c.getId().equals("cliente-a")
                && c.getProductos().isEmpty() && Long.valueOf(7).equals(c.getVersion())));
        assertEquals(2, original.getProductos().size());
        verify(carritoDAO).findById("cliente-a");
        verifyNoMoreInteractions(carritoDAO);
        verifyNoInteractions(productoDAO);
    }

    @Test
    void descartarCarritoInexistenteNoCreaDocumento() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.empty());
        var respuesta = servicio.descartarCarrito();
        assertTrue(respuesta.productos().isEmpty());
        assertEquals(new java.math.BigDecimal("0.00"), respuesta.importe());
        verify(carritoDAO, never()).save(any());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void puedeDescartarUnCarritoYaVacio() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(new Carrito("cliente-a")));
        assertTrue(servicio.descartarCarrito().productos().isEmpty());
        verify(carritoDAO).save(argThat(c -> c.getProductos().isEmpty()));
        verifyNoInteractions(productoDAO);
    }

    @Test
    void descarteReintentaConVersionRecienteSinMutarOriginales() {
        prepararCliente();
        Carrito anterior = new Carrito("cliente-a").conCantidad("producto-1", 2);
        Carrito reciente = anterior.conCantidad("otro", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(anterior, "version", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(reciente, "version", 2L);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(anterior), Optional.of(reciente));
        when(carritoDAO.save(any())).thenThrow(new OptimisticLockingFailureException("conflicto"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertTrue(servicio.descartarCarrito().productos().isEmpty());
        verify(carritoDAO).save(argThat(c -> Long.valueOf(1).equals(c.getVersion()) && c.getProductos().isEmpty()));
        verify(carritoDAO).save(argThat(c -> Long.valueOf(2).equals(c.getVersion()) && c.getProductos().isEmpty()));
        assertEquals(1, anterior.getProductos().size());
        assertEquals(2, reciente.getProductos().size());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void descarteLimitaConflictosATresIntentos() {
        prepararCliente();
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(
                new Carrito("cliente-a").conCantidad("producto-1", 2)));
        when(carritoDAO.save(any())).thenThrow(new OptimisticLockingFailureException("conflicto"));
        assertThrows(OptimisticLockingFailureException.class, () -> servicio.descartarCarrito());
        verify(carritoDAO, times(3)).findById("cliente-a");
        verify(carritoDAO, times(3)).save(any());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void descarteNoMutaCarritoSiFallaPersistencia() {
        prepararCliente();
        Carrito original = new Carrito("cliente-a").conCantidad("producto-1", 2);
        when(carritoDAO.findById("cliente-a")).thenReturn(Optional.of(original));
        when(carritoDAO.save(any())).thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(DataAccessResourceFailureException.class, () -> servicio.descartarCarrito());
        assertEquals(2, original.cantidadDe("producto-1"));
        verify(carritoDAO).save(any());
        verifyNoInteractions(productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_VENDEDOR", "ROLE_ADMIN", "sin-sesion", "bloqueado", "inactivo"})
    void descarteExigeClienteActivoConSesion(String caso) {
        if (caso.equals("sin-sesion")) {
            SecurityContextHolder.clearContext();
        } else if (caso.startsWith("ROLE_")) {
            autenticar(caso);
        } else {
            prepararCliente();
            if (caso.equals("bloqueado")) cliente.setBloqueada(true);
            if (caso.equals("inactivo")) cliente.setActivada(false);
        }
        assertThrows(AccessDeniedException.class, () -> servicio.descartarCarrito());
        verifyNoInteractions(carritoDAO, productoDAO);
    }
}
