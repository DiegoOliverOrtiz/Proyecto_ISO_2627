package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.PedidoVendedorResponse;
import es.uclm.esibuy.backend.exceptions.PedidoVendedorNoEncontradoException;
import es.uclm.esibuy.backend.exceptions.TransicionEstadoPedidoException;
import es.uclm.esibuy.backend.model.CambioEstadoPedido;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Vendedor;

@ExtendWith(MockitoExtension.class)
class PedidosVendedorServiceTests {

    private static final String EMAIL = "vendedor@test.com";
    private static final String VENDEDOR_ID = "vendedor-1";
    private static final String OTRO_VENDEDOR = "vendedor-2";

    @Mock
    private PedidoDAO pedidoDAO;

    @Mock
    private VendedorDAO vendedorDAO;

    @InjectMocks
    private PedidosVendedorService servicio;

    @BeforeEach
    void prepararVendedor() {
        Vendedor vendedor = new Vendedor();
        vendedor.setId(VENDEDOR_ID);
        vendedor.setEmail(EMAIL);
        vendedor.setActivada(true);
        vendedor.setBloqueada(false);

        lenient().when(vendedorDAO.findByEmail(EMAIL))
                .thenReturn(Optional.of(vendedor));
    }

    private LineaPedido linea(
            String productoId,
            String vendedorId,
            int cantidad,
            String precio) {

        return new LineaPedido(
                productoId,
                "Producto " + productoId,
                new BigDecimal(precio),
                cantidad,
                vendedorId,
                "Vendedor de prueba");
    }

    private Pedido pedido(
            String id,
            EstadoPedido estado,
            LineaPedido... lineas) {

        Instant fecha = Instant.parse("2026-11-02T12:00:00Z");

        return new Pedido(
                id,
                "cliente-1",
                List.of(lineas),
                estado,
                new BigDecimal("999.00"),
                fecha,
                estado == EstadoPedido.EN_PROCESO
                        ? null
                        : fecha,
                null,
                "metodo-pago-privado");
    }

    @Test
    void consultaPedidosActivosDelVendedor() {
        Pedido pagado = pedido(
                "pedido-1",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-1", VENDEDOR_ID, 2, "10.00"));

        Pedido enviado = pedido(
                "pedido-2",
                EstadoPedido.TRAMITADO_ENVIADO,
                linea("producto-2", VENDEDOR_ID, 1, "30.00"));

        when(pedidoDAO.findByVendedorId(VENDEDOR_ID))
                .thenReturn(List.of(pagado, enviado));

        List<PedidoVendedorResponse> resultado = servicio.consultarActivos(EMAIL);

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(
                p -> p.id().equals("pedido-1")));
        assertTrue(resultado.stream().anyMatch(
                p -> p.id().equals("pedido-2")));
    }

    @Test
    void noMuestraPedidosSinPagar() {
        Pedido sinPagar = pedido(
                "pedido-pendiente",
                EstadoPedido.EN_PROCESO,
                linea("producto-1", VENDEDOR_ID, 1, "20.00"));

        when(pedidoDAO.findByVendedorId(VENDEDOR_ID))
                .thenReturn(List.of(sinPagar));

        assertTrue(servicio.consultarActivos(EMAIL).isEmpty());
        assertTrue(servicio.consultarHistorico(EMAIL).isEmpty());
    }

    @Test
    void separaActivosEHistorico() {
        Pedido activo = pedido(
                "pedido-activo",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("p1", VENDEDOR_ID, 1, "10.00"));

        Pedido finalizado = pedido(
                "pedido-finalizado",
                EstadoPedido.FINALIZADO,
                linea("p2", VENDEDOR_ID, 1, "20.00"));

        when(pedidoDAO.findByVendedorId(VENDEDOR_ID))
                .thenReturn(List.of(activo, finalizado));

        List<PedidoVendedorResponse> activos = servicio.consultarActivos(EMAIL);

        List<PedidoVendedorResponse> historico = servicio.consultarHistorico(EMAIL);

        assertEquals(1, activos.size());
        assertEquals("pedido-activo", activos.get(0).id());

        assertEquals(1, historico.size());
        assertEquals("pedido-finalizado", historico.get(0).id());
    }

    @Test
    void muestraElMarcadorCuandoElClienteSeHaDadoDeBaja() {
        Pedido anonimo = pedido(
                "pedido-anonimo",
                EstadoPedido.FINALIZADO,
                linea("producto-1", VENDEDOR_ID, 1, "20.00"));
        anonimo.setClienteNombre("cliente eliminado");
        when(pedidoDAO.findByVendedorId(VENDEDOR_ID)).thenReturn(List.of(anonimo));

        List<PedidoVendedorResponse> resultado = servicio.consultarHistorico(EMAIL);

        assertEquals(1, resultado.size());
        assertEquals("cliente eliminado", resultado.getFirst().clienteNombre());
    }

    @Test
    void pedidoMixtoSoloMuestraProductosPropios() {
        Pedido mixto = pedido(
                "pedido-mixto",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-mio", VENDEDOR_ID, 2, "12.50"),
                linea("producto-ajeno", OTRO_VENDEDOR, 3, "80.00"));

        when(pedidoDAO.findById("pedido-mixto"))
                .thenReturn(Optional.of(mixto));

        PedidoVendedorResponse respuesta = servicio.consultarDetalle(EMAIL, "pedido-mixto");

        assertEquals(1, respuesta.productos().size());
        assertEquals(
                "producto-mio",
                respuesta.productos().get(0).productoId());

        assertEquals(
                0,
                new BigDecimal("25.00").compareTo(
                        respuesta.importeProductosPropios()));
    }

    @Test
    void pedidoMixtoNoExponeImporteGlobal() {
        Pedido mixto = pedido(
                "pedido-mixto",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("mio", VENDEDOR_ID, 1, "15.00"),
                linea("ajeno", OTRO_VENDEDOR, 1, "100.00"));

        when(pedidoDAO.findById("pedido-mixto"))
                .thenReturn(Optional.of(mixto));

        PedidoVendedorResponse respuesta = servicio.consultarDetalle(EMAIL, "pedido-mixto");

        assertEquals(
                0,
                new BigDecimal("15.00").compareTo(
                        respuesta.importeProductosPropios()));
        assertEquals(1, respuesta.productos().size());
    }

    @Test
    void rechazaConsultarPedidoDeOtroVendedor() {
        Pedido ajeno = pedido(
                "pedido-ajeno",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-ajeno", OTRO_VENDEDOR, 1, "40.00"));

        when(pedidoDAO.findById("pedido-ajeno"))
                .thenReturn(Optional.of(ajeno));

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.consultarDetalle(
                        EMAIL, "pedido-ajeno"));
    }

    @Test
    void rechazaPedidoInexistente() {
        when(pedidoDAO.findById("no-existe"))
                .thenReturn(Optional.empty());

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.consultarDetalle(
                        EMAIL, "no-existe"));
    }

    @Test
    void rechazaDetalleDePedidoSinPagar() {
        Pedido pendiente = pedido(
                "pedido-pendiente",
                EstadoPedido.EN_PROCESO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-pendiente"))
                .thenReturn(Optional.of(pendiente));

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.consultarDetalle(
                        EMAIL, "pedido-pendiente"));
    }

    @Test
    void vendedorInexistenteNoPuedeConsultarPedidos() {
        when(vendedorDAO.findByEmail("desconocido@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.consultarActivos(
                        "desconocido@test.com"));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void vendedorBloqueadoNoPuedeConsultarPedidos() {
        Vendedor bloqueado = new Vendedor();
        bloqueado.setId(VENDEDOR_ID);
        bloqueado.setActivada(true);
        bloqueado.setBloqueada(true);

        when(vendedorDAO.findByEmail(EMAIL))
                .thenReturn(Optional.of(bloqueado));

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.consultarActivos(EMAIL));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void vendedorNoActivadoNoPuedeConsultarPedidos() {
        Vendedor noActivado = new Vendedor();
        noActivado.setId(VENDEDOR_ID);
        noActivado.setActivada(false);
        noActivado.setBloqueada(false);

        when(vendedorDAO.findByEmail(EMAIL))
                .thenReturn(Optional.of(noActivado));

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.consultarHistorico(EMAIL));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void correoVacioNoPuedeConsultarPedidos() {
        assertThrows(
                AccessDeniedException.class,
                () -> servicio.consultarActivos(" "));

        verifyNoInteractions(pedidoDAO);
    }

    // HU-32: aceptar pedidos pagados (Pagado/Completado -> Tramitado/Enviado)

    @Test
    void aceptaPedidoPagadoPropio() {
        Pedido pagado = pedido(
                "pedido-1",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-1", VENDEDOR_ID, 2, "10.00"));
        Pedido enviado = pedido(
                "pedido-1",
                EstadoPedido.TRAMITADO_ENVIADO,
                linea("producto-1", VENDEDOR_ID, 2, "10.00"));

        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pagado))
                .thenReturn(Optional.of(enviado));
        when(pedidoDAO.actualizarEstado(eq("pedido-1"), eq("PAGADO_COMPLETADO"), eq(VENDEDOR_ID), eq("TRAMITADO_ENVIADO"), anyList()))
                .thenReturn(1L);

        PedidoVendedorResponse respuesta = servicio.aceptarPedido(EMAIL, "pedido-1");

        assertEquals(EstadoPedido.TRAMITADO_ENVIADO, respuesta.estado());
        verify(pedidoDAO).actualizarEstado(eq("pedido-1"), eq("PAGADO_COMPLETADO"), eq(VENDEDOR_ID), eq("TRAMITADO_ENVIADO"), anyList());
    }

    @Test
    void aceptarPedidoMixtoSoloDevuelveProductosPropios() {
        Pedido mixto = pedido(
                "pedido-mixto",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("mio", VENDEDOR_ID, 1, "15.00"),
                linea("ajeno", OTRO_VENDEDOR, 1, "100.00"));
        Pedido enviado = pedido(
                "pedido-mixto",
                EstadoPedido.TRAMITADO_ENVIADO,
                linea("mio", VENDEDOR_ID, 1, "15.00"),
                linea("ajeno", OTRO_VENDEDOR, 1, "100.00"));

        when(pedidoDAO.findById("pedido-mixto"))
                .thenReturn(Optional.of(mixto))
                .thenReturn(Optional.of(enviado));
        when(pedidoDAO.actualizarEstado(eq("pedido-mixto"), eq("PAGADO_COMPLETADO"), eq(VENDEDOR_ID), eq("TRAMITADO_ENVIADO"), anyList()))
                .thenReturn(1L);

        PedidoVendedorResponse respuesta = servicio.aceptarPedido(EMAIL, "pedido-mixto");

        assertEquals(1, respuesta.productos().size());
        assertEquals("mio", respuesta.productos().get(0).productoId());
    }

    @Test
    void noAceptaPedidoDeOtroVendedor() {
        Pedido ajeno = pedido(
                "pedido-ajeno",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-ajeno", OTRO_VENDEDOR, 1, "40.00"));

        when(pedidoDAO.findById("pedido-ajeno"))
                .thenReturn(Optional.of(ajeno));

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-ajeno"));

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any(), anyList());
    }

    @Test
    void noAceptaPedidoInexistente() {
        when(pedidoDAO.findById("no-existe"))
                .thenReturn(Optional.empty());

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.aceptarPedido(EMAIL, "no-existe"));

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any(), anyList());
    }

    @Test
    void noAceptaPedidoSinPagar() {
        Pedido pendiente = pedido(
                "pedido-pendiente",
                EstadoPedido.EN_PROCESO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-pendiente"))
                .thenReturn(Optional.of(pendiente));

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-pendiente"));

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any(), anyList());
    }

    @Test
    void noAceptaPedidoYaEnviado() {
        Pedido enviado = pedido(
                "pedido-enviado",
                EstadoPedido.TRAMITADO_ENVIADO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-enviado"))
                .thenReturn(Optional.of(enviado));

        assertThrows(
                TransicionEstadoPedidoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-enviado"));

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any(), anyList());
    }

    @Test
    void noAceptaPedidoFinalizado() {
        Pedido finalizado = pedido(
                "pedido-finalizado",
                EstadoPedido.FINALIZADO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-finalizado"))
                .thenReturn(Optional.of(finalizado));

        assertThrows(
                TransicionEstadoPedidoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-finalizado"));

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any(), anyList());
    }

    @Test
    void rechazaAceptarSiElPedidoCambioEntreMedias() {
        Pedido pagado = pedido(
                "pedido-1",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pagado));
        when(pedidoDAO.actualizarEstado(eq("pedido-1"), eq("PAGADO_COMPLETADO"), eq(VENDEDOR_ID), eq("TRAMITADO_ENVIADO"), anyList()))
                .thenReturn(0L);

        assertThrows(
                TransicionEstadoPedidoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-1"));
    }

    @Test
    void rechazaAceptarSinIdentificador() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.aceptarPedido(EMAIL, " "));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void vendedorBloqueadoNoPuedeAceptarPedidos() {
        Vendedor bloqueado = new Vendedor();
        bloqueado.setId(VENDEDOR_ID);
        bloqueado.setActivada(true);
        bloqueado.setBloqueada(true);

        when(vendedorDAO.findByEmail(EMAIL))
                .thenReturn(Optional.of(bloqueado));

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-1"));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void vendedorInexistenteNoPuedeAceptarPedidos() {
        when(vendedorDAO.findByEmail("desconocido@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.aceptarPedido("desconocido@test.com", "pedido-1"));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void correoVacioNoPuedeAceptarPedidos() {
        assertThrows(
                AccessDeniedException.class,
                () -> servicio.aceptarPedido(" ", "pedido-1"));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void aceptarPedidoMixtoUsaSoloElVendedorDeLaSesion() {
        Pedido mixto = pedido(
                "pedido-mixto",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("mio", VENDEDOR_ID, 1, "15.00"),
                linea("ajeno", OTRO_VENDEDOR, 1, "100.00"));

        when(pedidoDAO.findById("pedido-mixto"))
                .thenReturn(Optional.of(mixto));
        when(pedidoDAO.actualizarEstado(any(), any(), any(), any(), anyList()))
                .thenReturn(1L);

        servicio.aceptarPedido(EMAIL, "pedido-mixto");

        verify(pedidoDAO).actualizarEstado(eq("pedido-mixto"), eq("PAGADO_COMPLETADO"), eq(VENDEDOR_ID), eq("TRAMITADO_ENVIADO"), anyList());
        verify(pedidoDAO, never()).actualizarEstado(any(), any(), eq(OTRO_VENDEDOR), any(), anyList());
    }

    @Test
    void pedidoAceptadoSigueEnActivosComoEnviadoYNoPasaAlHistorico() {
        Pedido enviado = pedido(
                "pedido-1",
                EstadoPedido.TRAMITADO_ENVIADO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findByVendedorId(VENDEDOR_ID))
                .thenReturn(List.of(enviado));

        List<PedidoVendedorResponse> activos = servicio.consultarActivos(EMAIL);

        assertEquals(1, activos.size());
        assertEquals(EstadoPedido.TRAMITADO_ENVIADO, activos.get(0).estado());
        assertTrue(servicio.consultarHistorico(EMAIL).isEmpty());
    }

    @Test
    void rechazaAceptarSiElPedidoDesapareceTrasActualizar() {
        Pedido pagado = pedido(
                "pedido-1",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-1", VENDEDOR_ID, 1, "10.00"));

        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pagado))
                .thenReturn(Optional.empty());
        when(pedidoDAO.actualizarEstado(any(), any(), any(), any(), anyList()))
                .thenReturn(1L);

        assertThrows(
                PedidoVendedorNoEncontradoException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-1"));
    }

    @Test
    void vendedorNoActivadoNoPuedeAceptarPedidos() {
        Vendedor noActivado = new Vendedor();
        noActivado.setId(VENDEDOR_ID);
        noActivado.setActivada(false);
        noActivado.setBloqueada(false);

        when(vendedorDAO.findByEmail(EMAIL))
                .thenReturn(Optional.of(noActivado));

        assertThrows(
                AccessDeniedException.class,
                () -> servicio.aceptarPedido(EMAIL, "pedido-1"));

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void aceptarPedidoRegistraLaTransicionEnElHistorial() {
        Pedido pagado = pedido(
                "pedido-historial",
                EstadoPedido.PAGADO_COMPLETADO,
                linea("producto-1", VENDEDOR_ID, 1, "15.00"));

        Pedido enviado = pagado.cambiarEstado(
                EstadoPedido.TRAMITADO_ENVIADO,
                Instant.parse("2026-11-05T10:00:00Z"));

        when(pedidoDAO.findById("pedido-historial"))
                .thenReturn(Optional.of(pagado))
                .thenReturn(Optional.of(enviado));

        when(pedidoDAO.actualizarEstado(
                eq("pedido-historial"),
                eq("PAGADO_COMPLETADO"),
                eq(VENDEDOR_ID),
                eq("TRAMITADO_ENVIADO"),
                anyList()))
                .thenReturn(1L);

        servicio.aceptarPedido(EMAIL, "pedido-historial");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CambioEstadoPedido>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(pedidoDAO).actualizarEstado(
                eq("pedido-historial"),
                eq("PAGADO_COMPLETADO"),
                eq(VENDEDOR_ID),
                eq("TRAMITADO_ENVIADO"),
                captor.capture());

        List<CambioEstadoPedido> historial = captor.getValue();

        assertFalse(historial.isEmpty());

        assertEquals(
                EstadoPedido.TRAMITADO_ENVIADO,
                historial.getLast().estado());

        assertNotNull(historial.getLast().fecha());
    }
}
