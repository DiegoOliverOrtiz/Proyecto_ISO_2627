package es.uclm.esibuy.backend.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;

class TotpSecurityStoreTests {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final TotpSecurityStore store = new TotpSecurityStore(mongo);

    @Test
    void reservaElIntentoConLimitePorCuentaYReiniciaSoloLasVentanasCaducadas() {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("seguridad_totp")))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null), UpdateResult.acknowledged(1, 1L, null));
        assertTrue(store.reservarIntento("usuario-1", now));
        var queries = ArgumentCaptor.forClass(Query.class);
        var updates = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(queries.capture(), updates.capture(), eq("seguridad_totp"));
        var reset = queries.getAllValues().get(0).getQueryObject();
        assertEquals("intentos:usuario-1", reset.getString("_id"));
        assertEquals(Date.from(now.minusSeconds(300)), reset.get("inicio", Document.class).get("$lte"));
        var reserve = queries.getAllValues().get(1).getQueryObject();
        assertEquals("intentos:usuario-1", reserve.getString("_id"));
        assertEquals(5, reserve.get("intentos", Document.class).get("$lt"));
        assertEquals(1, updates.getAllValues().get(1).getUpdateObject().get("$inc", Document.class).get("intentos"));
    }

    @Test
    void rechazaElIntentoCuandoLaActualizacionAtomicaNoEncuentraCupo() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("seguridad_totp")))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertFalse(store.reservarIntento("usuario-1", Instant.now()));
    }

    @Test
    void dosPeticionesSoloPuedenConsumirElIntervaloSiMongoLoAvanza() throws Exception {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("seguridad_totp")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(0, 0L, null));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> store.consumirIntervalo("usuario-1", "secret", 123));
            var second = executor.submit(() -> store.consumirIntervalo("usuario-1", "secret", 123));
            assertNotEquals(first.get(), second.get());
        }
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).updateFirst(query.capture(), any(Update.class), eq("seguridad_totp"));
        for (Query value : query.getAllValues()) {
            assertEquals(123L, value.getQueryObject().get("ultimoIntervalo", Document.class).get("$lt"));
            assertFalse(value.getQueryObject().toJson().contains("secret"));
        }
    }

    @Test
    void elIndiceUnicoImpideQueDosServidoresConsumanElMismoRespaldo() throws Exception {
        var insertedIds = ConcurrentHashMap.<String>newKeySet();
        when(mongo.insert(any(Document.class), eq("seguridad_totp"))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            if (!insertedIds.add(document.getString("_id"))) {
                throw new DuplicateKeyException("_id duplicado");
            }
            return document;
        });
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return store.consumirRespaldo("usuario-1", "hash"); });
            var second = executor.submit(() -> { start.await(); return store.consumirRespaldo("usuario-1", "hash"); });
            start.countDown();
            assertNotEquals(first.get(), second.get());
        }
        assertEquals(1, insertedIds.size());
    }
}
