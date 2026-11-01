package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Base64;
import java.util.stream.Stream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;

class TotpKeyMigrationTests {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final String oldKey = Base64.getEncoder().encodeToString(new byte[32]);
    private final TotpCryptoService oldCrypto = new TotpCryptoService(oldKey);

    private TotpCryptoService newCrypto() {
        byte[] bytes = new byte[32];
        bytes[0] = 1;
        return new TotpCryptoService(Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void migraElSecretoSinCambiarElIdNiSobrescribirOtrosCampos() {
        var crypto = newCrypto();
        ObjectId id = new ObjectId();
        String encrypted = oldCrypto.cifrar("SECRET");
        Document document = new Document("_id", id).append("secretoTotpCifrado", encrypted);
        when(mongo.stream(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(Stream.of(document), Stream.empty());
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("usuarios")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        new TotpKeyMigration(mongo, crypto, oldKey).run(null);

        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq("usuarios"));
        assertEquals(id, query.getValue().getQueryObject().get("_id"));
        assertEquals(encrypted, query.getValue().getQueryObject().get("secretoTotpCifrado"));
        Document fields = update.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals(1, fields.size());
        assertEquals("SECRET", crypto.descifrar(fields.getString("secretoTotpCifrado")));
    }

    @Test
    void reiniciarNoVuelveAMigrarLosSecretosYaActualizados() {
        var crypto = newCrypto();
        Document document = new Document("_id", new ObjectId())
                .append("secretoTotpCifrado", crypto.cifrar("SECRET"));
        when(mongo.stream(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(Stream.of(document), Stream.empty());
        new TotpKeyMigration(mongo, crypto, oldKey).run(null);
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }

    @Test
    void unSecretoIlegibleDetieneLaMigracionSinBorrarlo() {
        Document document = new Document("_id", new ObjectId()).append("secretoTotpCifrado", "alterado");
        when(mongo.stream(any(Query.class), eq(Document.class), eq("usuarios"))).thenReturn(Stream.of(document));
        assertThrows(IllegalStateException.class, () -> new TotpKeyMigration(mongo, newCrypto(), oldKey).run(null));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }
}
