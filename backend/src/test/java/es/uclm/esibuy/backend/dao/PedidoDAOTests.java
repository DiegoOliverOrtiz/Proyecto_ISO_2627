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
    void elCambioDeEstadoActualizaEstadoEHistorialSinModificarOtrosDatos()
            throws Exception {

        String actualizacion = actualizarEstado()
                .getAnnotation(Update.class)
                .value()
                .replace(" ", "");

        assertEquals(
                "{'$set':{'estado':?3,'historialEstados':?4}}",
                actualizacion);
    }

    @Test
    void elCambioDeEstadoDevuelveElNumeroDeDocumentosModificados() throws Exception {
        assertEquals(long.class, actualizarEstado().getReturnType());
    }

    // HU-33: comprobación de pedidos en curso antes de eliminar un producto.
    @Test
    void laComprobacionDePedidosFiltraPorProductoYEstados() throws Exception {
        Query query = PedidoDAO.class
                .getMethod("existsByProductoIdAndEstadoIn", String.class, java.util.List.class)
                .getAnnotation(Query.class);
        String filtro = query.value().replace(" ", "");

        assertTrue(query.exists());
        assertTrue(filtro.contains("'lineas.productoId':?0"));
        assertTrue(filtro.contains("'estado':{'$in':?1}"));
    }
}
