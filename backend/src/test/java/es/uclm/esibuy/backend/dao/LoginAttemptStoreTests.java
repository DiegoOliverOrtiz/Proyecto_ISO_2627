package es.uclm.esibuy.backend.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;

class LoginAttemptStoreTests {
    private static final String COLLECTION = "seguridad_login";
    private static final String PEPPER = "test-pepper-that-is-never-used-outside-tests-123456";
    private final MongoTemplate mongo = mock(MongoTemplate.class);

    @Test
    void reservaCincoIntentosPorParYGuardaSoloClavesSeudonimizadas() {
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION)))
                .thenReturn(null,
                        new Document("users", List.of()),
                        new Document("users", List.of("otro-usuario-hasheado")));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null),
                        UpdateResult.acknowledged(0, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null));

        LoginAttemptStore store = new LoginAttemptStore(mongo, PEPPER);
        assertEquals(0, store.reservarIntento("192.0.2.10", "ana@example.com", now));

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongo, org.mockito.Mockito.times(4)).updateFirst(queries.capture(), any(Update.class), eq(COLLECTION));
        Document pairReservation = queries.getAllValues().get(1).getQueryObject();
        assertEquals(5, pairReservation.get("attempts", Document.class).get("$lt"));
        assertFalse(pairReservation.toJson().contains("192.0.2.10"));
        assertFalse(pairReservation.toJson().contains("ana@example.com"));
        assertEquals(Date.from(now.minusSeconds(300)),
                queries.getAllValues().get(0).getQueryObject().get("windowStart", Document.class).get("$lte"));
    }

    @Test
    void escalaBloqueoDeCuentaDesdeTreintaSegundosHastaCincoMinutos() {
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        AtomicInteger reads = new AtomicInteger();
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION))).thenAnswer(invocation -> {
            int step = reads.incrementAndGet() - 1;
            return new Document("failures", 5 + step).append("backoffStep", step);
        });
        AtomicInteger updates = new AtomicInteger();
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION))).thenAnswer(invocation -> {
            int call = updates.incrementAndGet();
            return call % 3 == 1
                    ? UpdateResult.acknowledged(0, 0L, null)
                    : UpdateResult.acknowledged(1, 1L, null);
        });

        LoginAttemptStore store = new LoginAttemptStore(mongo, PEPPER);
        assertEquals(30, store.registrarFallo("ana@example.com", now));
        assertEquals(120, store.registrarFallo("ana@example.com", now.plusSeconds(31)));
        assertEquals(240, store.registrarFallo("ana@example.com", now.plusSeconds(152)));
        assertEquals(300, store.registrarFallo("ana@example.com", now.plusSeconds(393)));
    }

    @Test
    void umbralAdaptativoPorIpLimitaAUnTotalDeDosIntentosPorVentana() {
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        List<String> usersBeforeThreshold = java.util.stream.IntStream.range(0, 19)
                .mapToObj(index -> "user-" + index)
                .toList();
        List<String> usersAtThreshold = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> "user-" + index)
                .toList();
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION)))
                .thenReturn(null,
                        new Document("users", usersBeforeThreshold),
                        new Document("users", usersAtThreshold),
                        new Document("users", usersAtThreshold)
                                .append("windowStart", Date.from(now.minusSeconds(100)))
                                .append("adaptiveAttempts", 2));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null),
                        UpdateResult.acknowledged(0, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null),
                        UpdateResult.acknowledged(0, 0L, null));

        LoginAttemptStore store = new LoginAttemptStore(mongo, PEPPER);
        assertEquals(200, store.reservarIntento("192.0.2.20", "nuevo@example.com", now));

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongo, org.mockito.Mockito.times(5)).updateFirst(queries.capture(), any(Update.class), eq(COLLECTION));
        assertEquals(2, queries.getAllValues().get(4).getQueryObject()
                .get("adaptiveAttempts", Document.class).get("$lt"));
    }
    @Test
    void correoUsaVentanaDeDiezMinutosCincoEnviosYClaveNormalizada() {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION)))
                .thenReturn(new Document("windowStart", Date.from(now.minusSeconds(100))));
        LoginAttemptStore store = new LoginAttemptStore(mongo, PEPPER);
        assertEquals(500, store.reservarEnvioOtp(" ANA@EXAMPLE.COM ", now));
        assertEquals(500, store.reservarEnvioOtp("ana@example.com", now));
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongo, org.mockito.Mockito.times(4)).updateFirst(queries.capture(), any(Update.class), eq(COLLECTION));
        Document reserva = queries.getAllValues().get(1).getQueryObject();
        assertEquals(5, reserva.get("attempts", Document.class).get("$lt"));
        assertEquals(Date.from(now.minusSeconds(600)), reserva.get("windowStart", Document.class).get("$gt"));
        assertEquals(reserva.get("_id"), queries.getAllValues().get(3).getQueryObject().get("_id"));
        assertFalse(reserva.toJson().contains("ana@example.com"));
    }
}