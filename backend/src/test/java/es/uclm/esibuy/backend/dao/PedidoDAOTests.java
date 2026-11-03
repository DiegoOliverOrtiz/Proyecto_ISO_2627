package es.uclm.esibuy.backend.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

/**
 * HU-32: la consulta atómica de cambio de estado se crea al arrancar el contexto (BackendApplicationTests);
 * aquí se fija su contenido para que no se pierdan las condiciones de seguridad.
 */
class PedidoDAOTests {

    private Method actualizarEstado() throws NoSuchMethodException {
        return PedidoDAO.class.getMethod(
                "actualizarEstado", String.class, String.class, String.class, String.class);
    }

    @Test
    void elCambioDeEstadoFiltraPorPedidoEstadoActualYVendedor() throws Exception {
        String filtro = actualizarEstado().getAnnotation(Query.class).value().replace(" ", "");

        assertTrue(filtro.contains("'_id':?0"));
        assertTrue(filtro.contains("'estado':?1"));
        assertTrue(filtro.contains("'lineas.vendedorId':?2"));
    }

    @Test
    void elCambioDeEstadoSoloModificaElEstado() throws Exception {
        String actualizacion = actualizarEstado().getAnnotation(Update.class).value().replace(" ", "");

        // El resto del documento (líneas, importe, fechas, cliente) queda igual que lo ve el cliente.
        assertEquals("{'$set':{'estado':?3}}", actualizacion);
    }

    @Test
    void elCambioDeEstadoDevuelveElNumeroDeDocumentosModificados() throws Exception {
        assertEquals(long.class, actualizarEstado().getReturnType());
    }
}
