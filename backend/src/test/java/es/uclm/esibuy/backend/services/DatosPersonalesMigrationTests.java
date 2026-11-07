package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;

class DatosPersonalesMigrationTests {

    private static final String CLAVE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void cifraDniYTelefonoLegadosSinModificarOtrosCampos() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DatosPersonalesCryptoService cifrado = new DatosPersonalesCryptoService(CLAVE);
        Document documento = new Document("_id", "cliente-1")
                .append("dniNif", "12345678A")
                .append("telefono", "600123123")
                .append("nombre", "Ana");
        when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(Stream.of(documento));
        UpdateResult resultado = mock(UpdateResult.class);
        when(resultado.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("usuarios")))
                .thenReturn(resultado);

        new DatosPersonalesMigration(mongoTemplate, cifrado)
                .run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Update> actualizaciones = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<Query> consultas = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(2))
                .updateFirst(consultas.capture(), actualizaciones.capture(), eq("usuarios"));

        assertEquals(2, actualizaciones.getAllValues().size());
        for (int indice = 0; indice < actualizaciones.getAllValues().size(); indice++) {
            Document actualizacion = actualizaciones.getAllValues().get(indice)
                    .getUpdateObject()
                    .get("$set", Document.class);
            String campo = actualizacion.keySet().iterator().next();
            String valorEsperado = campo.equals("dniNif") ? "12345678A" : "600123123";
            assertEquals(valorEsperado, cifrado.descifrar(actualizacion.getString(campo), campo));
            assertEquals(valorEsperado, consultas.getAllValues().get(indice)
                    .getQueryObject().get(campo));
        }
    }

    @Test
    void noVuelveACifrarCamposYaMigrados() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DatosPersonalesCryptoService cifrado = new DatosPersonalesCryptoService(CLAVE);
        Document documento = new Document("_id", "cliente-1")
                .append("dniNif", cifrado.cifrar("12345678A", "dniNif"))
                .append("telefono", cifrado.cifrar("600123123", "telefono"));
        when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(Stream.of(documento));

        new DatosPersonalesMigration(mongoTemplate, cifrado)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(mongoTemplate, times(0))
                .updateFirst(any(Query.class), any(Update.class), eq("usuarios"));
    }
}
