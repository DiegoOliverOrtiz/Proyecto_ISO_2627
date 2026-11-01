package es.uclm.esibuy.backend.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import es.uclm.esibuy.backend.model.Producto;

@ExtendWith(MockitoExtension.class)
class ProductoActualizacionDAOTests {
    @Mock private MongoTemplate template;
    private ProductoActualizacionDAOImpl dao;
    private Producto producto;

    @BeforeEach
    void configurar() {
        dao = new ProductoActualizacionDAOImpl(template);
        producto = new Producto();
        producto.setId("producto");
        producto.setPrecio(100.0);
        producto.setStock(5);
    }

    @Test
    void decrementaConFiltroDeStockYDatosValidados() {
        when(template.updateFirst(any(Query.class), any(Update.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        dao.descontarStock(producto, 2);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(query.capture(), update.capture(), eq(Producto.class));
        var filtro = query.getValue().getQueryObject();
        assertEquals(2, filtro.get("stock", org.bson.Document.class).get("$gte"));
        assertEquals(true, filtro.get("esVisible"));
        assertEquals(100.0, filtro.get("precio"));
        assertEquals(0, filtro.get("descuentoPremium"));
        assertEquals(-2, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).get("stock"));
    }

    @Test
    void descuentoNoAplicadoRechazaLaCompra() {
        when(template.updateFirst(any(Query.class), any(Update.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThrows(IllegalArgumentException.class, () -> dao.descontarStock(producto, 2));
    }

    @Test
    void noPermiteIncrementarStockConCantidadNegativa() {
        assertThrows(IllegalArgumentException.class, () -> dao.descontarStock(producto, -1));
        verifyNoInteractions(template);
    }

    @Test
    void visibilidadNoEscribeElStockLeidoAnteriormente() {
        when(template.updateFirst(any(Query.class), any(Update.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        dao.actualizarVisibilidad("producto", false);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(any(Query.class), update.capture(), eq(Producto.class));
        assertEquals(new org.bson.Document("$set", new org.bson.Document("esVisible", false)),
                update.getValue().getUpdateObject());
    }

    @Test
    void edicionComparaStockAnteriorSinSobrescribirVisibilidadOVendedor() {
        when(template.updateFirst(any(Query.class), any(Update.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThrows(OptimisticLockingFailureException.class, () -> dao.actualizarDatos(producto, 8));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(query.capture(), update.capture(), eq(Producto.class));
        assertEquals(8, query.getValue().getQueryObject().get("stock"));
        var campos = update.getValue().getUpdateObject().get("$set", org.bson.Document.class);
        assertFalse(campos.containsKey("esVisible"));
        assertFalse(campos.containsKey("vendedor"));
    }
}
