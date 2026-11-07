package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class DatosPersonalesKeyRotationTests {

    private static final String CLAVE_ANTERIOR = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String CLAVE_NUEVA = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

    @Test
    void rotaSoloLosCamposCifradosConLaClaveAnterior() {
        DatosPersonalesCryptoService cifradoAnterior =
                new DatosPersonalesCryptoService(CLAVE_ANTERIOR);
        DatosPersonalesCryptoService cifradoActual =
                new DatosPersonalesCryptoService(CLAVE_NUEVA, CLAVE_ANTERIOR);
        String dniAnterior = cifradoAnterior.cifrar("12345678A", "dniNif");
        String telefonoAnterior = cifradoAnterior.cifrar("600123123", "telefono");
        String telefonoActual = cifradoActual.cifrar("600999888", "telefono");
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Document documento = new Document("_id", "cliente-1")
                .append("dniNif", dniAnterior)
                .append("telefono", telefonoAnterior);
        Document documentoYaRotado = new Document("_id", "cliente-2")
                .append("dniNif", cifradoActual.cifrar("87654321B", "dniNif"))
                .append("telefono", telefonoActual);
        when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(Stream.of(documento, documentoYaRotado));
        UpdateResult resultado = mock(UpdateResult.class);
        when(resultado.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("usuarios")))
                .thenReturn(resultado);

        new DatosPersonalesKeyRotation(mongoTemplate, cifradoActual, false)
                .run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Update> actualizaciones = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2))
                .updateFirst(any(Query.class), actualizaciones.capture(), eq("usuarios"));
        for (Update actualizacion : actualizaciones.getAllValues()) {
            Document camposActualizados = actualizacion.getUpdateObject().get("$set", Document.class);
            String campo = camposActualizados.keySet().iterator().next();
            String valorRotado = camposActualizados.getString(campo);
            String valorAnterior = campo.equals("dniNif") ? dniAnterior : telefonoAnterior;
            String textoPlano = campo.equals("dniNif") ? "12345678A" : "600123123";

            assertNotEquals(valorAnterior, valorRotado);
            assertEquals(textoPlano, cifradoActual.descifrar(valorRotado, campo));
        }
    }

    @Test
    void exigeClaveAnteriorDistintaYNoPermiteMezclarMigracionInicial() {
        DatosPersonalesCryptoService sinClaveAnterior =
                new DatosPersonalesCryptoService(CLAVE_NUEVA);
        DatosPersonalesCryptoService mismaClave =
                new DatosPersonalesCryptoService(CLAVE_NUEVA, CLAVE_NUEVA);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new DatosPersonalesKeyRotation(mongoTemplate, sinClaveAnterior, false));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new DatosPersonalesKeyRotation(mongoTemplate, mismaClave, false));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new DatosPersonalesKeyRotation(
                        mongoTemplate,
                        new DatosPersonalesCryptoService(CLAVE_NUEVA, CLAVE_ANTERIOR),
                        true));
    }
}
