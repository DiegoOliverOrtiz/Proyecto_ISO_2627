package es.uclm.esibuy.backend.model;

import static org.junit.jupiter.api.Assertions.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

class UsuarioEmailOtpPersistenciaTests {
    @Test
    void documentosAntiguosDesactivadosYPreferenciaConservadaAlLeerMongo() throws Exception {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        Cliente antiguo = converter.read(Cliente.class, new Document("email", "cliente@example.com"));
        assertFalse(antiguo.isEmailOtpActivado());
        antiguo.setEmailOtpActivado(true);
        Document guardado = new Document();
        converter.write(antiguo, guardado);
        assertEquals(true, guardado.getBoolean("emailOtpActivado"));
        assertTrue(converter.read(Cliente.class, guardado).isEmailOtpActivado());
    }
}
