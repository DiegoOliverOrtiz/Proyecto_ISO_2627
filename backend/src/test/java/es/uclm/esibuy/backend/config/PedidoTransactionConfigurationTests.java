package es.uclm.esibuy.backend.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.transaction.support.TransactionTemplate;

class PedidoTransactionConfigurationTests {
    @Test
    void reintentaSoloCommitSiSuResultadoEsDesconocido() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        ClientSession session = mock(ClientSession.class);
        when(factory.getSession(any())).thenReturn(session);
        MongoException error = new MongoException("Resultado desconocido");
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(error).doNothing().when(session).commitTransaction();
        AtomicInteger ejecuciones = new AtomicInteger();
        new TransactionTemplate(new PedidoTransactionConfiguration().pedidoTransactionManager(factory))
                .execute(status -> ejecuciones.incrementAndGet());
        assertEquals(1, ejecuciones.get());
        verify(session, times(2)).commitTransaction();
    }

    @Test
    void limitaReintentosDeCommitIncierto() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        ClientSession session = mock(ClientSession.class);
        when(factory.getSession(any())).thenReturn(session);
        MongoException error = new MongoException("Resultado desconocido");
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(error).when(session).commitTransaction();
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(
                new PedidoTransactionConfiguration().pedidoTransactionManager(factory))
                .execute(status -> null));
        verify(session, times(3)).commitTransaction();
    }
}
