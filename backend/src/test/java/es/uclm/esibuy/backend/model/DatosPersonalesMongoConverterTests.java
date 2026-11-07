package es.uclm.esibuy.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import es.uclm.esibuy.backend.config.DatosPersonalesMongoConfiguration;
import es.uclm.esibuy.backend.services.DatosPersonalesCryptoService;

class DatosPersonalesMongoConverterTests {

    private static final String CLAVE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private MappingMongoConverter conversor;

    @BeforeEach
    void configurarConversor() throws Exception {
        var conversiones = new DatosPersonalesMongoConfiguration()
                .mongoCustomConversions(new DatosPersonalesCryptoService(CLAVE));
        MongoMappingContext contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversiones);
        conversor.afterPropertiesSet();
    }

    @Test
    void cifraDniYTelefonoAlEscribirYLosDescifraAlLeer() {
        Cliente cliente = new Cliente();
        cliente.setDniNif("12345678A");
        cliente.setTelefono("+34 600123123");
        Document documento = new Document();

        conversor.write(cliente, documento);

        assertTrue(documento.getString("dniNif").startsWith("enc:v1:"));
        assertTrue(documento.getString("telefono").startsWith("enc:v1:"));
        assertNotEquals("12345678A", documento.getString("dniNif"));
        assertNotEquals("+34 600123123", documento.getString("telefono"));

        Cliente recuperado = conversor.read(Cliente.class, documento);

        assertEquals("12345678A", recuperado.getDniNif());
        assertEquals("+34 600123123", recuperado.getTelefono());
    }

    @Test
    void cifraTelefonoParaTodosLosTiposDeUsuario() {
        Vendedor vendedor = new Vendedor();
        vendedor.setTelefono("600123123");
        vendedor.setDniNif("12345678A");
        Document documentoVendedor = new Document();
        conversor.write(vendedor, documentoVendedor);

        assertTrue(documentoVendedor.getString("telefono").startsWith("enc:v1:"));
        assertTrue(documentoVendedor.getString("dniNif").startsWith("enc:v1:"));
        assertEquals("600123123", conversor.read(Vendedor.class, documentoVendedor).getTelefono());

        Administrador administrador = new Administrador();
        administrador.setTelefono("600123123");
        Document documentoAdministrador = new Document();
        conversor.write(administrador, documentoAdministrador);

        assertTrue(documentoAdministrador.getString("telefono").startsWith("enc:v1:"));
        assertEquals("600123123", conversor.read(Administrador.class, documentoAdministrador).getTelefono());
    }

    @Test
    void leeValoresLegadosEnClaroParaPermitirSuMigracion() {
        Document documento = new Document("dniNif", "12345678A")
                .append("telefono", "600123123");

        Cliente recuperado = conversor.read(Cliente.class, documento);

        assertEquals("12345678A", recuperado.getDniNif());
        assertEquals("600123123", recuperado.getTelefono());
    }
}
