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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.dto.PedidoVendedorResponse;
import es.uclm.esibuy.backend.exceptions.PedidoVendedorNoEncontradoException;
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
}
