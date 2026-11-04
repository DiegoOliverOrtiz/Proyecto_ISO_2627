package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mongodb.MongoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import es.uclm.esibuy.backend.dao.*;
import es.uclm.esibuy.backend.dto.PedidoConsultaResponse;
import es.uclm.esibuy.backend.exceptions.CancelacionPedidoException;
import es.uclm.esibuy.backend.model.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTests {
    @Mock private ClienteDAO clientes;
    @Mock private CarritoDAO carritos;
    @Mock private ProductoDAO productos;
    @Mock private MetodoPagoDAO metodos;
    @Mock private VendedorDAO vendedores;
    @Mock private PedidoDAO pedidos;
    @Mock private MongoTransactionManager transacciones;
    private PedidoService servicio;
    private Cliente cliente;
    private Producto producto;
    private Carrito carrito;
    private String operacion;

    @BeforeEach
    void configurar() {
        servicio = new PedidoService(new CarritoService(carritos, clientes, productos), carritos, productos,
                metodos, vendedores, pedidos, transacciones);
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(true);
        Direccion direccion = new Direccion();
        direccion.setId("direccion-a");
        direccion.setCalle("Calle original");
        cliente.agregarDireccion(direccion);
        producto = new Producto();
        producto.setId("producto-1");
        producto.setNombre("Nombre original");
        producto.setPrecio(100.0);
        producto.setDescuento(10);
        producto.setDescuentoPremium(25);
        producto.setStock(10);
        producto.setVendedor("Tienda original");
        producto.setEsVisible(true);
        carrito = new Carrito("cliente-a").conCantidad("producto-1", 2);
        operacion = UUID.randomUUID().toString();
        autenticar("ROLE_CLIENTE");
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "cliente@example.com", null, List.of(new SimpleGrantedAuthority(rol))));
    }

    private void cuenta() {
        when(clientes.findByEmail("cliente@example.com")).thenReturn(Optional.of(cliente));
    }

    private void seleccion() {
        cuenta();
        MetodoPago metodo = new MetodoPago();
        metodo.setId("metodo-a");
        metodo.setClienteId("cliente-a");
        when(metodos.findByClienteId("cliente-a")).thenReturn(List.of(metodo));
    }

    private void compra() {
        seleccion();
        when(carritos.findById("cliente-a")).thenReturn(Optional.of(carrito));
        when(productos.findById("producto-1")).thenReturn(Optional.of(producto));
        Vendedor vendedor = new Vendedor();
        vendedor.setId("vendedor-1");
        vendedor.setNombreComercial("Tienda original");
        lenient().when(vendedores.findByNombreComercial("Tienda original")).thenReturn(Optional.of(vendedor));
    }

    private Pedido pagar() { return servicio.realizarPedido("direccion-a", "metodo-a", operacion); }

    private Pedido pedidoConEstado(String id, EstadoPedido estadoFinal) {
        Instant fechaInicial = Instant.parse("2026-11-01T10:00:00Z");
        Pedido pedido = new Pedido(id, "cliente-a",
                List.of(new LineaPedido("producto-1", "Teclado", new BigDecimal("20.00"),
                        1, "vendedor-1", "Tienda")),
                EstadoPedido.EN_PROCESO, new BigDecimal("20.00"),
                fechaInicial, null, null, null);
        EstadoPedido[] camino = {
                EstadoPedido.EN_PROCESO,
                EstadoPedido.PAGADO_COMPLETADO,
                EstadoPedido.TRAMITADO_ENVIADO,
                EstadoPedido.FINALIZADO
        };
        for (int indice = 1; indice < camino.length; indice++) {
            if (camino[indice - 1] == estadoFinal) {
                break;
            }
            pedido = pedido.cambiarEstado(camino[indice], fechaInicial.plusSeconds(indice * 60L));
        }
        return pedido;
    }

    @Test
    void consultaSeparaPedidosActivosEHistoricosYConservaSeguimientoSinEscribir() {
        cuenta();
        List<Pedido> delCliente = List.of(
                pedidoConEstado("pedido-proceso", EstadoPedido.EN_PROCESO),
                pedidoConEstado("pedido-pagado", EstadoPedido.PAGADO_COMPLETADO),
                pedidoConEstado("pedido-enviado", EstadoPedido.TRAMITADO_ENVIADO),
                pedidoConEstado("pedido-finalizado", EstadoPedido.FINALIZADO));
        when(pedidos.findByClienteId("cliente-a")).thenReturn(delCliente);

        List<PedidoConsultaResponse> activos = servicio.consultarPedidosActivos();
        List<PedidoConsultaResponse> historico = servicio.consultarHistoricoPedidos();

        assertEquals(List.of("pedido-proceso", "pedido-pagado", "pedido-enviado"),
                activos.stream().map(PedidoConsultaResponse::id).toList());
        assertEquals(List.of("pedido-finalizado"),
                historico.stream().map(PedidoConsultaResponse::id).toList());
        assertEquals(List.of(EstadoPedido.EN_PROCESO, EstadoPedido.PAGADO_COMPLETADO,
                        EstadoPedido.TRAMITADO_ENVIADO, EstadoPedido.FINALIZADO),
                historico.getFirst().historialEstados().stream()
                        .map(cambio -> cambio.estado()).toList());
        assertEquals(4, historico.getFirst().historialEstados().size());

        verify(pedidos, times(2)).findByClienteId("cliente-a");
        verify(pedidos, never()).save(any(Pedido.class));
        verify(pedidos, never()).delete(any(Pedido.class));
    }

    @Test
    void consultasSinPedidosDevuelvenListasVacias() {
        cuenta();
        when(pedidos.findByClienteId("cliente-a")).thenReturn(List.of());

        assertTrue(servicio.consultarPedidosActivos().isEmpty());
        assertTrue(servicio.consultarHistoricoPedidos().isEmpty());

        verify(pedidos, times(2)).findByClienteId("cliente-a");
        verify(pedidos, never()).save(any(Pedido.class));
    }

    @Test
    void detalleDevuelveHistorialPropioYOcultaPedidoAjenoComoNoEncontrado() {
        cuenta();
        Pedido propio = pedidoConEstado("pedido-propio", EstadoPedido.FINALIZADO);
        when(pedidos.findByIdAndClienteId("pedido-propio", "cliente-a"))
                .thenReturn(Optional.of(propio));
        when(pedidos.findByIdAndClienteId("pedido-ajeno", "cliente-a"))
                .thenReturn(Optional.empty());

        PedidoConsultaResponse detalle = servicio.consultarPedido("pedido-propio");

        assertEquals("pedido-propio", detalle.id());
        assertEquals(EstadoPedido.FINALIZADO, detalle.estado());
        assertEquals(4, detalle.historialEstados().size());
        assertEquals(Instant.parse("2026-11-01T10:03:00Z"),
                detalle.historialEstados().getLast().fecha());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> servicio.consultarPedido("pedido-ajeno"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(pedidos).findByIdAndClienteId("pedido-propio", "cliente-a");
        verify(pedidos).findByIdAndClienteId("pedido-ajeno", "cliente-a");
        verify(pedidos, never()).findById(anyString());
        verify(pedidos, never()).save(any(Pedido.class));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"EN_PROCESO", "PAGADO_COMPLETADO"})
    void cancelaPedidosPermitidosYReponeStockSoloSiEstabaPagado(EstadoPedido estado) {
        cuenta();
        Pedido pedido = pedidoConEstado("pedido-cancelable", estado);
        when(pedidos.findByIdAndClienteId("pedido-cancelable", "cliente-a"))
                .thenReturn(Optional.of(pedido));
        when(pedidos.cancelarPedido(
                eq("pedido-cancelable"),
                eq("cliente-a"),
                eq(estado.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList()))
                .thenReturn(1L);

        PedidoConsultaResponse respuesta = servicio.cancelarPedido("pedido-cancelable");

        assertEquals(EstadoPedido.CANCELADO, respuesta.estado());
        assertEquals(EstadoPedido.CANCELADO,
                respuesta.historialEstados().getLast().estado());
        verify(pedidos).cancelarPedido(
                eq("pedido-cancelable"),
                eq("cliente-a"),
                eq(estado.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList());

        if (estado == EstadoPedido.PAGADO_COMPLETADO) {
            verify(productos, times(1)).reponerStock("producto-1", 1);
        } else {
            verify(productos, never()).reponerStock(anyString(), anyInt());
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {
            "TRAMITADO_ENVIADO", "FINALIZADO", "CANCELADO"
    })
    void rechazaPedidoEnviadoFinalizadoOYaCancelado(EstadoPedido estado) {
        cuenta();
        Pedido pedido = estado == EstadoPedido.CANCELADO
                ? pedidoConEstado("pedido-no-cancelable", EstadoPedido.PAGADO_COMPLETADO)
                        .cancelar(Instant.parse("2026-11-04T10:02:00Z"))
                : pedidoConEstado("pedido-no-cancelable", estado);
        when(pedidos.findByIdAndClienteId("pedido-no-cancelable", "cliente-a"))
                .thenReturn(Optional.of(pedido));

        CancelacionPedidoException error = assertThrows(
                CancelacionPedidoException.class,
                () -> servicio.cancelarPedido("pedido-no-cancelable"));

        assertFalse(error.getMessage().isBlank());
        verify(pedidos, never()).cancelarPedido(
                anyString(), anyString(), anyString(), anyString(), anyList());
        verify(productos, never()).reponerStock(anyString(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pedido-ajeno", "pedido-inexistente"})
    void pedidoAjenoOInexistenteDevuelve404SinReponerStock(String pedidoId) {
        cuenta();
        when(pedidos.findByIdAndClienteId(pedidoId, "cliente-a"))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.cancelarPedido(pedidoId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(pedidos).findByIdAndClienteId(pedidoId, "cliente-a");
        verify(pedidos, never()).findById(anyString());
        verify(pedidos, never()).cancelarPedido(
                anyString(), anyString(), anyString(), anyString(), anyList());
        verify(productos, never()).reponerStock(anyString(), anyInt());
    }

    @Test
    void segundoIntentoDeCancelacionNoReponeStockOtraVez() {
        cuenta();
        Pedido pagado = pedidoConEstado("pedido-duplicado", EstadoPedido.PAGADO_COMPLETADO);
        Pedido cancelado = pagado.cancelar(Instant.parse("2026-11-04T10:02:00Z"));
        when(pedidos.findByIdAndClienteId("pedido-duplicado", "cliente-a"))
                .thenReturn(Optional.of(pagado), Optional.of(cancelado));
        when(pedidos.cancelarPedido(
                eq("pedido-duplicado"),
                eq("cliente-a"),
                eq(EstadoPedido.PAGADO_COMPLETADO.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList()))
                .thenReturn(1L);

        servicio.cancelarPedido("pedido-duplicado");
        assertThrows(CancelacionPedidoException.class,
                () -> servicio.cancelarPedido("pedido-duplicado"));

        verify(pedidos, times(1)).cancelarPedido(
                eq("pedido-duplicado"),
                eq("cliente-a"),
                eq(EstadoPedido.PAGADO_COMPLETADO.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList());
        verify(productos, times(1)).reponerStock("producto-1", 1);
    }

    @Test
    void conflictoConcurrenteNoReponeStock() {
        cuenta();
        Pedido pedido = pedidoConEstado("pedido-concurrente", EstadoPedido.PAGADO_COMPLETADO);
        when(pedidos.findByIdAndClienteId("pedido-concurrente", "cliente-a"))
                .thenReturn(Optional.of(pedido));
        when(pedidos.cancelarPedido(
                eq("pedido-concurrente"),
                eq("cliente-a"),
                eq(EstadoPedido.PAGADO_COMPLETADO.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList()))
                .thenReturn(0L);

        assertThrows(CancelacionPedidoException.class,
                () -> servicio.cancelarPedido("pedido-concurrente"));

        verify(pedidos).cancelarPedido(
                eq("pedido-concurrente"),
                eq("cliente-a"),
                eq(EstadoPedido.PAGADO_COMPLETADO.name()),
                eq(EstadoPedido.CANCELADO.name()),
                anyList());
        verify(productos, never()).reponerStock(anyString(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void compraCalculadaEnServidorConSnapshotYCarritoVacio(boolean premium) {
        cliente.setEsPremium(premium);
        compra();
        Pedido pedido = pagar();
        assertEquals(EstadoPedido.PAGADO_COMPLETADO, pedido.getEstado());
        assertEquals(new BigDecimal(premium ? "150.00" : "180.00"), pedido.getImporte());
        assertEquals(new BigDecimal(premium ? "75.00" : "90.00"), pedido.getLineas().getFirst().precioCompra());
        assertEquals("vendedor-1", pedido.getLineas().getFirst().vendedorId());
        assertNotNull(pedido.getFechaPago());
        verify(productos).descontarStock(producto, 2);
        verify(carritos).save(argThat(c -> c.getProductos().isEmpty() && c.getId().equals("cliente-a")));
        verify(pedidos).insert(argThat((Pedido p) -> p.getEstado() == EstadoPedido.EN_PROCESO));
        verify(pedidos).save(pedido);
        verify(transacciones).commit(any());
        producto.setNombre("Otro nombre");
        producto.setPrecio(1.0);
        cliente.eliminarDireccion("direccion-a");
        assertEquals("Nombre original", pedido.getLineas().getFirst().nombreProducto());
        assertEquals("Calle original", pedido.getDireccionEnvio().getCalle());
    }

    @Test
    void repeticionDevuelveElMismoPedidoSinDescontarOtraVez() {
        compra();
        AtomicReference<Pedido> guardado = new AtomicReference<>();
        when(pedidos.findById(anyString())).thenAnswer(i -> Optional.ofNullable(guardado.get()));
        doAnswer(i -> { guardado.set(i.getArgument(0)); return guardado.get(); }).when(pedidos).save(any());
        Pedido primero = pagar();
        assertSame(primero, pagar());
        verify(productos, times(1)).descontarStock(any(), anyInt());
        verify(carritos, times(1)).save(any());
        verify(pedidos, times(1)).insert(any(Pedido.class));
        assertThrows(es.uclm.esibuy.backend.exceptions.OperacionPedidoConflictivaException.class,
                () -> servicio.realizarPedido("direccion-otra", "metodo-a", operacion));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void carritoVacioOInexistenteNoEscribe(boolean existe) {
        seleccion();
        when(carritos.findById("cliente-a")).thenReturn(
                existe ? Optional.of(new Carrito("cliente-a")) : Optional.empty());
        assertThrows(IllegalArgumentException.class, this::pagar);
        verifyNoInteractions(productos);
        verify(pedidos, never()).insert(any(Pedido.class));
        verify(transacciones).rollback(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eliminado", "oculto", "stock", "agotado", "premium", "precio", "cantidad"})
    void productoNoAdquiribleRechazaSinEscrituras(String motivo) {
        switch (motivo) {
            case "oculto" -> producto.setEsVisible(false);
            case "stock" -> producto.setStock(1);
            case "agotado" -> producto.setStock(0);
            case "premium" -> producto.setSoloPremium(true);
            case "precio" -> producto.setPrecio(Double.NaN);
            case "cantidad" -> carrito = carrito.conCantidad("producto-1", 0);
            default -> { }
        }
        compra();
        if (motivo.equals("eliminado")) when(productos.findById("producto-1")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, this::pagar);
        verify(productos, never()).descontarStock(any(), anyInt());
        verify(pedidos, never()).insert(any(Pedido.class));
        verify(carritos, never()).save(any());
        verify(transacciones).rollback(any());
    }

    @Test
    void direccionAjenaOInexistenteNoEscribe() {
        cuenta();
        assertThrows(IllegalArgumentException.class,
                () -> servicio.realizarPedido("direccion-b", "metodo-a", operacion));
        verifyNoInteractions(metodos, carritos, productos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void metodoAjenoOInexistenteNoEscribe(boolean ajeno) {
        cuenta();
        MetodoPago metodo = new MetodoPago();
        metodo.setId("metodo-a");
        metodo.setClienteId("cliente-b");
        when(metodos.findByClienteId("cliente-a")).thenReturn(ajeno ? List.of(metodo) : List.of());
        assertThrows(IllegalArgumentException.class, this::pagar);
        verifyNoInteractions(carritos, productos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_VENDEDOR", "ROLE_USER", "sin-sesion"})
    void noAutorizaRolesIncorrectos(String rol) {
        if (rol.equals("sin-sesion")) SecurityContextHolder.clearContext(); else autenticar(rol);
        assertThrows(AccessDeniedException.class, this::pagar);
        verifyNoInteractions(clientes, carritos, productos, pedidos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bloqueado", "inactivo", "inexistente"})
    void rechazaCuentaNoActiva(String estado) {
        cliente.setBloqueada(estado.equals("bloqueado"));
        cliente.setActivada(!estado.equals("inactivo"));
        when(clientes.findByEmail(anyString())).thenReturn(
                estado.equals("inexistente") ? Optional.empty() : Optional.of(cliente));
        assertThrows(AccessDeniedException.class, this::pagar);
        verifyNoInteractions(carritos, productos, pedidos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stock", "pedido", "carrito"})
    void falloDeEscrituraSolicitaRollbackSinCommit(String punto) {
        compra();
        RuntimeException error = new IllegalStateException("Persistencia no disponible");
        switch (punto) {
            case "stock" -> doThrow(error).when(productos).descontarStock(any(), anyInt());
            case "pedido" -> doThrow(error).when(pedidos).save(any());
            case "carrito" -> doThrow(error).when(carritos).save(any());
            default -> throw new AssertionError();
        }
        assertSame(error, assertThrows(RuntimeException.class, this::pagar));
        verify(transacciones).rollback(any());
        verify(transacciones, never()).commit(any());
    }

    @Test
    void noReejecutaCompraSiElCommitEsIncierto() {
        compra();
        MongoException error = new MongoException("Commit incierto");
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(error).when(transacciones).commit(any());
        assertThrows(MongoException.class, this::pagar);
        verify(productos, times(1)).descontarStock(any(), anyInt());
    }

    @Test
    void limitaReintentosTransitorios() {
        cuenta();
        MongoException error = new MongoException("Conflicto transitorio");
        error.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        when(pedidos.findById(anyString())).thenThrow(error);
        assertThrows(MongoException.class, this::pagar);
        verify(transacciones, times(3)).rollback(any());
        verifyNoInteractions(productos);
    }

    @Test
    void vendedorEliminadoImpideUnaCompraNueva() {
        compra();
        when(vendedores.findByNombreComercial(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, this::pagar);
        verify(pedidos, never()).insert(any(Pedido.class));
    }

    @Test
    void sumaVariasLineasConPreciosDelServidor() {
        carrito = carrito.conCantidad("producto-2", 3);
        compra();
        Producto segundo = new Producto();
        segundo.setId("producto-2");
        segundo.setPrecio(10.0);
        segundo.setStock(5);
        segundo.setEsVisible(true);
        segundo.setVendedor("Tienda original");
        when(productos.findById("producto-2")).thenReturn(Optional.of(segundo));
        Pedido pedido = pagar();
        assertEquals(new BigDecimal("210.00"), pedido.getImporte());
        assertEquals(2, pedido.getLineas().size());
        verify(productos).descontarStock(segundo, 3);
    }

    @Test
    void falloAlInsertarNoDescuentaStock() {
        compra();
        doThrow(new IllegalStateException("Fallo de inserción")).when(pedidos).insert(any(Pedido.class));
        assertThrows(IllegalStateException.class, this::pagar);
        verify(productos, never()).descontarStock(any(), anyInt());
        verify(transacciones).rollback(any());
    }

    @Test
    void inicioDeTransaccionFallidoNoEjecutaLaCompra() {
        when(transacciones.getTransaction(any())).thenThrow(new IllegalStateException("Sin transacciones"));
        assertThrows(IllegalStateException.class, this::pagar);
        verifyNoInteractions(clientes, pedidos, productos, carritos);
    }

    @Test
    void noAdmiteClavesDeOperacionInvalidas() {
        reset(transacciones);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.realizarPedido("direccion-a", "metodo-a", "clave falsa"));
        verifyNoInteractions(transacciones, clientes, pedidos, productos, carritos);
    }

    @Test
    void clienteConfirmaRecepcionDePedidoEnviado() {
        cuenta();

        Pedido pedido = pedidoConEstado(
                "pedido-enviado",
                EstadoPedido.TRAMITADO_ENVIADO);

        when(pedidos.findByIdAndClienteId("pedido-enviado", "cliente-a"))
                .thenReturn(Optional.of(pedido));

        when(pedidos.finalizarPedido(
                eq("pedido-enviado"),
                eq("cliente-a"),
                eq(EstadoPedido.TRAMITADO_ENVIADO.name()),
                eq(EstadoPedido.FINALIZADO.name()),
                anyList()))
                .thenReturn(1L);

        PedidoConsultaResponse respuesta =
                servicio.confirmarRecepcion("pedido-enviado");

        assertEquals(EstadoPedido.FINALIZADO, respuesta.estado());

        assertEquals(EstadoPedido.FINALIZADO,
                respuesta.historialEstados().getLast().estado());

        assertEquals(4, respuesta.historialEstados().size());

        verify(pedidos).finalizarPedido(
                eq("pedido-enviado"),
                eq("cliente-a"),
                eq(EstadoPedido.TRAMITADO_ENVIADO.name()),
                eq(EstadoPedido.FINALIZADO.name()),
                anyList());

        verify(pedidos, never()).save(any(Pedido.class));
        verifyNoInteractions(productos);
    }

    @Test
    void clienteNoPuedeConfirmarUnPedidoAjenoOInexistente() {
        cuenta();

        when(pedidos.findByIdAndClienteId("pedido-ajeno", "cliente-a"))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.confirmarRecepcion("pedido-ajeno"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(pedidos).findByIdAndClienteId(
                "pedido-ajeno", "cliente-a");

        verify(pedidos, never()).findById(anyString());
        verify(pedidos, never()).save(any(Pedido.class));
        verifyNoInteractions(productos);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {
            "EN_PROCESO",
            "PAGADO_COMPLETADO",
            "FINALIZADO",
            "CANCELADO"
    })
    void noPermiteConfirmarRecepcionSiPedidoNoEstaEnviado(
            EstadoPedido estado) {
        cuenta();

        Pedido pedido = estado == EstadoPedido.CANCELADO
                ? pedidoConEstado(
                        "pedido-no-enviado",
                        EstadoPedido.PAGADO_COMPLETADO)
                        .cancelar(Instant.parse("2026-11-04T10:02:00Z"))
                : pedidoConEstado("pedido-no-enviado", estado);

        when(pedidos.findByIdAndClienteId(
                "pedido-no-enviado", "cliente-a"))
                .thenReturn(Optional.of(pedido));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.confirmarRecepcion("pedido-no-enviado"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(pedidos, never()).finalizarPedido(
                anyString(), anyString(), anyString(),
                anyString(), anyList());

        verify(pedidos, never()).save(any(Pedido.class));
        verifyNoInteractions(productos);
    }

    @Test
    void conflictoConcurrenteImpideFinalizarPedido() {
        cuenta();

        Pedido pedido = pedidoConEstado(
                "pedido-concurrente",
                EstadoPedido.TRAMITADO_ENVIADO);

        when(pedidos.findByIdAndClienteId(
                "pedido-concurrente", "cliente-a"))
                .thenReturn(Optional.of(pedido));

        when(pedidos.finalizarPedido(
                eq("pedido-concurrente"),
                eq("cliente-a"),
                eq(EstadoPedido.TRAMITADO_ENVIADO.name()),
                eq(EstadoPedido.FINALIZADO.name()),
                anyList()))
                .thenReturn(0L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.confirmarRecepcion("pedido-concurrente"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(pedidos).finalizarPedido(
                eq("pedido-concurrente"),
                eq("cliente-a"),
                eq(EstadoPedido.TRAMITADO_ENVIADO.name()),
                eq(EstadoPedido.FINALIZADO.name()),
                anyList());

        verify(pedidos, never()).save(any(Pedido.class));
        verifyNoInteractions(productos);
    }
}
