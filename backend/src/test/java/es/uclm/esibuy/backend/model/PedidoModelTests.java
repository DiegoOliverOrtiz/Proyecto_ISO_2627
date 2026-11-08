package es.uclm.esibuy.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class PedidoModelTests {
    private MappingMongoConverter converter;

    @BeforeEach
    void configurarConversor() throws Exception {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> { });
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @ParameterizedTest
    @EnumSource(EstadoPedido.class)
    void conservaPedidoAlConvertirDocumentoMongo(EstadoPedido estado) {
        Instant fecha = Instant.parse("2026-10-31T12:00:00Z");
        LineaPedido linea = new LineaPedido("producto-1", "Producto comprado", new BigDecimal("80.25"),
                2, "vendedor-1", "Tienda original");
        Pedido pedido = Pedido.builder().clienteId("cliente-1").lineas(List.of(linea)).estado(estado)
                .importe(new BigDecimal("160.50")).fechaCreacion(fecha)
                .fechaPago(estado == EstadoPedido.EN_PROCESO ? null : fecha)
                .direccionEnvio(direccion()).metodoPagoId("metodo-1").build();

        Document documento = new Document();
        converter.write(pedido, documento);
        documento.put("_id", "pedido-1");
        assertEquals("pedido", documento.getString("_class"));
        assertEquals(estado.name(), documento.getString("estado"));
        assertEquals(new Decimal128(new BigDecimal("160.50")), documento.get("importe"));
        Document lineaMongo = documento.getList("lineas", Document.class).getFirst();
        assertEquals(new Decimal128(new BigDecimal("80.25")), lineaMongo.get("precioCompra"));
        assertFalse(lineaMongo.containsKey("$ref"));
        assertEquals("metodo-1", documento.getString("metodoPagoId"));
        Document direccionMongo = documento.get("direccionEnvio", Document.class);
        assertEquals("Calle original", direccionMongo.getString("calle"));
        assertFalse(direccionMongo.containsKey("$ref"));
        assertFalse(documento.containsKey("token"));
        assertFalse(documento.containsKey("numeroTarjeta"));
        assertFalse(documento.containsKey("cvv"));

        Pedido recuperado = converter.read(Pedido.class, documento);
        assertEquals("pedido-1", recuperado.getId());
        assertEquals(pedido.getClienteId(), recuperado.getClienteId());
        assertEquals(pedido.getLineas(), recuperado.getLineas());
        assertEquals(pedido.getEstado(), recuperado.getEstado());
        assertEquals(pedido.getImporte(), recuperado.getImporte());
        assertEquals(pedido.getFechaCreacion(), recuperado.getFechaCreacion());
        assertEquals(pedido.getFechaPago(), recuperado.getFechaPago());
        assertEquals(pedido.getMetodoPagoId(), recuperado.getMetodoPagoId());
        assertDireccionOriginal(recuperado.getDireccionEnvio());
    }

    @Test
    void conservaLineasSinDependerDeColeccionesMutablesNiReferenciasVivas() {
        List<LineaPedido> lineas = new ArrayList<>();
        LineaPedido original = new LineaPedido("producto-1", "Nombre original", new BigDecimal("10.00"),
                1, "vendedor-1", "Tienda original");
        lineas.add(original);
        Pedido pedido = Pedido.builder().clienteId("cliente-1").lineas(lineas).estado(EstadoPedido.EN_PROCESO)
                .importe(new BigDecimal("10.00")).fechaCreacion(Instant.now()).build();
        lineas.clear();

        assertEquals(List.of(original), pedido.getLineas());
        assertThrows(UnsupportedOperationException.class, () -> pedido.getLineas().clear());
        Document documento = new Document();
        converter.write(pedido, documento);
        // La lectura del histórico no necesita consultar productos ni vendedores.
        assertEquals(List.of(original), converter.read(Pedido.class, documento).getLineas());
    }

    @Test
    void conservaDireccionAunqueCambieLaOriginalOLaDevuelta() {
        Direccion original = direccion();
        Pedido pedido = Pedido.builder().clienteId("cliente-1").lineas(List.of()).estado(EstadoPedido.EN_PROCESO)
                .importe(BigDecimal.ZERO).fechaCreacion(Instant.now()).direccionEnvio(original)
                .metodoPagoId("metodo-1").build();
        original.setCalle("Otra calle");
        original.setPiso("99");
        pedido.getDireccionEnvio().setCiudad("Otra ciudad");
        assertDireccionOriginal(pedido.getDireccionEnvio());

        Document documento = new Document();
        converter.write(pedido, documento);
        Pedido recuperado = converter.read(Pedido.class, documento);
        recuperado.getDireccionEnvio().setNombre("Nombre cambiado");
        assertDireccionOriginal(recuperado.getDireccionEnvio());
    }

    @Test
    void conservaElNombreDelClienteAlMarcarElPedidoComoPagado() {
        Pedido pedido = Pedido.builder().clienteId("cliente-1").lineas(List.of()).estado(EstadoPedido.EN_PROCESO)
                .importe(BigDecimal.ZERO).fechaCreacion(Instant.now()).build();
        pedido.setClienteNombre("Ana García");

        Pedido pagado = pedido.pagado(Instant.now());

        assertEquals("Ana García", pagado.getClienteNombre());
    }

    @Test
    void puedeLeerDocumentosAnterioresSinDireccionNiMetodoPago() {
        Document documento = new Document("clienteId", "cliente-1")
                .append("lineas", List.of()).append("estado", "EN_PROCESO")
                .append("importe", new Decimal128(BigDecimal.ZERO));
        Pedido recuperado = converter.read(Pedido.class, documento);
        assertNull(recuperado.getDireccionEnvio());
        assertNull(recuperado.getMetodoPagoId());
    }

    private Direccion direccion() {
        Direccion direccion = new Direccion();
        direccion.setId("direccion-1");
        direccion.setNombre("Casa");
        direccion.setCalle("Calle original");
        direccion.setNumero("12");
        direccion.setPortal("B");
        direccion.setPiso("3A");
        direccion.setCodigoPostal("13001");
        direccion.setCiudad("Ciudad Real");
        direccion.setProvincia("Ciudad Real");
        return direccion;
    }

    private void assertDireccionOriginal(Direccion direccion) {
        assertEquals("direccion-1", direccion.getId());
        assertEquals("Casa", direccion.getNombre());
        assertEquals("Calle original", direccion.getCalle());
        assertEquals("12", direccion.getNumero());
        assertEquals("B", direccion.getPortal());
        assertEquals("3A", direccion.getPiso());
        assertEquals("13001", direccion.getCodigoPostal());
        assertEquals("Ciudad Real", direccion.getCiudad());
        assertEquals("Ciudad Real", direccion.getProvincia());
    }
}
