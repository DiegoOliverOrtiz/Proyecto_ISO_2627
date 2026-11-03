package es.uclm.esibuy.backend.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EstadoPedidoTests {

    @Test
    void permiteAvanzarAlEstadoSiguiente() {
        assertTrue(EstadoPedido.EN_PROCESO.permiteTransicionA(EstadoPedido.PAGADO_COMPLETADO));
        assertTrue(EstadoPedido.PAGADO_COMPLETADO.permiteTransicionA(EstadoPedido.TRAMITADO_ENVIADO));
        assertTrue(EstadoPedido.TRAMITADO_ENVIADO.permiteTransicionA(EstadoPedido.FINALIZADO));
    }

    @Test
    void rechazaSaltosDeEstado() {
        assertFalse(EstadoPedido.EN_PROCESO.permiteTransicionA(EstadoPedido.TRAMITADO_ENVIADO));
        assertFalse(EstadoPedido.EN_PROCESO.permiteTransicionA(EstadoPedido.FINALIZADO));
        assertFalse(EstadoPedido.PAGADO_COMPLETADO.permiteTransicionA(EstadoPedido.FINALIZADO));
    }

    @Test
    void unPedidoEnviadoSoloPuedeFinalizarse() {
        for (EstadoPedido destino : EstadoPedido.values()) {
            assertEquals(
                    destino == EstadoPedido.FINALIZADO,
                    EstadoPedido.TRAMITADO_ENVIADO.permiteTransicionA(destino),
                    () -> "TRAMITADO_ENVIADO -> " + destino);
        }
    }

    @Test
    void rechazaRetrocesosYRepeticiones() {
        assertFalse(EstadoPedido.TRAMITADO_ENVIADO.permiteTransicionA(EstadoPedido.PAGADO_COMPLETADO));
        assertFalse(EstadoPedido.FINALIZADO.permiteTransicionA(EstadoPedido.TRAMITADO_ENVIADO));
        assertFalse(EstadoPedido.PAGADO_COMPLETADO.permiteTransicionA(EstadoPedido.PAGADO_COMPLETADO));
        assertFalse(EstadoPedido.FINALIZADO.permiteTransicionA(null));
    }
}
