package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.ValoracionDAO;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Valoracion;

@ExtendWith(MockitoExtension.class)
class ValoracionServiceTests {
    private static final String EMAIL_CLIENTE = "cliente@example.com";
    private static final String CLIENTE_ID = "cliente-1";
    private static final String PEDIDO_ID = "pedido-1";
    private static final String PRODUCTO_ID = "producto-1";

    @Mock
    private ClienteDAO clienteDAO;

    @Mock
    private PedidoDAO pedidoDAO;

    @Mock
    private ValoracionDAO valoracionDAO;

    @Captor
    private ArgumentCaptor<Valoracion> valoracionCaptor;

    private ValoracionService valoracionService;

    @BeforeEach
    void setUp() {
        valoracionService = new ValoracionService(valoracionDAO, pedidoDAO, clienteDAO);

        Cliente cliente = new Cliente();
        cliente.setId(CLIENTE_ID);
        cliente.setEmail(EMAIL_CLIENTE);
        lenient().when(clienteDAO.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cliente));
        lenient().when(pedidoDAO.findByIdAndClienteId(PEDIDO_ID, CLIENTE_ID))
                .thenReturn(Optional.of(crearPedido(EstadoPedido.FINALIZADO)));
        lenient().when(valoracionDAO.existsByClienteIdAndProductoId(CLIENTE_ID, PRODUCTO_ID))
                .thenReturn(false);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 6 })
    void rechazaPuntuacionesFueraDelRangoDeUnoACinco(int puntuacion) {
        assertThrows(IllegalArgumentException.class,
                () -> valorar(puntuacion, "Buen producto"));

        verify(valoracionDAO, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5 })
    void aceptaLosLimitesDelRangoDePuntuacion(int puntuacion) {
        valorar(puntuacion, "Buen producto");

        verify(valoracionDAO).save(any(Valoracion.class));
    }

    @Test
    void rechazaComentariosQueSuperanLosQuinientosCaracteres() {
        String comentario = "a".repeat(501);

        assertThrows(IllegalArgumentException.class,
                () -> valorar(5, comentario));

        verify(valoracionDAO, never()).save(any());
    }

    @Test
    void aceptaUnComentarioDeQuinientosCaracteres() {
        String comentario = "a".repeat(500);

        valorar(5, comentario);

        verify(valoracionDAO).save(any(Valoracion.class));
    }

    @Test
    void soloPermiteValorarUnProductoDeUnPedidoFinalizadoDelCliente() {
        when(pedidoDAO.findByIdAndClienteId(PEDIDO_ID, CLIENTE_ID))
                .thenReturn(Optional.of(crearPedido(EstadoPedido.EN_PROCESO)));

        assertThrows(IllegalStateException.class,
                () -> valorar(5, "Buen producto"));

        verify(valoracionDAO, never()).save(any());
    }

    @Test
    void rechazaValorarUnProductoQueNoEstaEnElPedidoFinalizado() {
        when(pedidoDAO.findByIdAndClienteId(PEDIDO_ID, CLIENTE_ID))
                .thenReturn(Optional.of(new Pedido(
                        PEDIDO_ID,
                        CLIENTE_ID,
                        List.of(new LineaPedido(
                                "otro-producto",
                                "Otro producto",
                                BigDecimal.TEN,
                                1,
                                "vendedor-1",
                                "Tienda")),
                        EstadoPedido.FINALIZADO,
                        BigDecimal.TEN,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null)));

        assertThrows(IllegalArgumentException.class,
                () -> valorar(5, "Buen producto"));

        verify(valoracionDAO, never()).save(any());
    }

    @Test
    void noPermiteUnaSegundaValoracionDelMismoClienteYProducto() {
        when(valoracionDAO.existsByClienteIdAndProductoId(CLIENTE_ID, PRODUCTO_ID))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> valorar(1, "Cambio de opinión"));

        verify(valoracionDAO, never()).save(any());
    }

    @Test
    void guardaLaValoracionAsociadaAlClientePedidoYProductoAutenticados() {
        valorar(4, "Buen producto");

        verify(valoracionDAO).save(valoracionCaptor.capture());
        Valoracion guardada = valoracionCaptor.getValue();
        assertEquals(CLIENTE_ID, guardada.clienteId());
        assertEquals(PEDIDO_ID, guardada.pedidoId());
        assertEquals(PRODUCTO_ID, guardada.productoId());
        assertEquals(4, guardada.puntuacion());
        assertEquals("Buen producto", guardada.comentario());
    }

    @Test
    void calculaLaValoracionMediaDelProducto() {
        when(valoracionDAO.findByProductoId(PRODUCTO_ID)).thenReturn(List.of(
                new Valoracion(CLIENTE_ID, PEDIDO_ID, PRODUCTO_ID, 5, "Excelente"),
                new Valoracion("cliente-2", "pedido-2", PRODUCTO_ID, 3, "Correcto")));

        double media = valoracionService.calcularMediaProducto(PRODUCTO_ID);

        assertEquals(4.0, media);
    }

    private void valorar(int puntuacion, String comentario) {
        valoracionService.valorar(
                EMAIL_CLIENTE,
                PEDIDO_ID,
                PRODUCTO_ID,
                puntuacion,
                comentario);
    }

    private static Pedido crearPedido(EstadoPedido estado) {
        return new Pedido(
                PEDIDO_ID,
                CLIENTE_ID,
                List.of(new LineaPedido(
                        PRODUCTO_ID,
                        "Producto",
                        BigDecimal.TEN,
                        1,
                        "vendedor-1",
                        "Tienda")),
                estado,
                BigDecimal.TEN,
                Instant.now(),
                Instant.now(),
                null,
                null);
    }
}
