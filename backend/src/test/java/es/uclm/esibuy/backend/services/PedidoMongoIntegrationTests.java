package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import es.uclm.esibuy.backend.config.PedidoTransactionConfiguration;
import es.uclm.esibuy.backend.dao.*;
import es.uclm.esibuy.backend.model.*;

/** Requiere un replica set de pruebas. Nunca escribe en la base de datos de la URI. */
@EnabledIfEnvironmentVariable(named = "ESIBUY_TEST_MONGODB_URI", matches = ".+")
class PedidoMongoIntegrationTests {
    private MongoClient client;
    private MongoTemplate template;
    private PedidoService servicio;
    private ClienteDAO clientes;
    private CarritoDAO carritos;
    private ProductoDAO productos;
    private MetodoPagoDAO metodos;
    private PedidoDAO pedidos;
    private org.springframework.data.mongodb.MongoTransactionManager manager;

    @BeforeEach
    void configurar() {
        client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(System.getenv("ESIBUY_TEST_MONGODB_URI")))
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build());
        Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
        assertTrue(hello.containsKey("setName") || "isdbgrid".equals(hello.getString("msg")),
                "La integración exige replica set o mongos, no standalone.");
        // Atlas limita el nombre a 38 bytes en esta instancia.
        String database = "hu26_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        template = new MongoTemplate(client, database);
        for (String collection : List.of("usuarios", "productos", "carritos", "pedidos", "metodos_pago")) {
            template.createCollection(collection);
        }
        MongoRepositoryFactory factory = new MongoRepositoryFactory(template);
        clientes = factory.getRepository(ClienteDAO.class);
        carritos = factory.getRepository(CarritoDAO.class);
        productos = factory.getRepository(ProductoDAO.class, new ProductoActualizacionDAOImpl(template));
        metodos = factory.getRepository(MetodoPagoDAO.class);
        pedidos = factory.getRepository(PedidoDAO.class);
        VendedorDAO vendedores = factory.getRepository(VendedorDAO.class);
        manager = new PedidoTransactionConfiguration().pedidoTransactionManager(template.getMongoDatabaseFactory());
        servicio = new PedidoService(new CarritoService(carritos, clientes, productos), carritos, productos,
                metodos, vendedores, pedidos, manager);
        Vendedor vendedor = new Vendedor();
        vendedor.setNombreComercial("Tienda");
        vendedores.save(vendedor);
        Producto producto = new Producto();
        producto.setId("producto");
        producto.setNombre("Original");
        producto.setPrecio(100.0);
        producto.setStock(1);
        producto.setEsVisible(true);
        producto.setVendedor("Tienda");
        productos.save(producto);
        for (String id : List.of("a", "b")) {
            Cliente cliente = new Cliente();
            cliente.setId(id);
            cliente.setEmail(id + "@example.com");
            cliente.setActivada(true);
            Direccion direccion = new Direccion();
            direccion.setId("direccion-" + id);
            direccion.setCalle("Original");
            cliente.agregarDireccion(direccion);
            clientes.save(cliente);
            MetodoPago metodo = new MetodoPago();
            metodo.setId("metodo-" + id);
            metodo.setClienteId(id);
            metodos.save(metodo);
            carritos.save(new Carrito(id).conCantidad("producto", 1));
        }
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        try {
            if (template != null) {
                assertTrue(template.getDb().getName().matches("hu26_test_[0-9a-f]{24}"));
                // readWrite permite borrar colecciones; no exige el permiso adicional dropDatabase.
                for (String collection : List.of("usuarios", "productos", "carritos", "pedidos", "metodos_pago")) {
                    template.getCollection(collection).drop();
                }
                assertFalse(template.getDb().listCollectionNames().iterator().hasNext());
            }
        } finally {
            if (client != null) client.close();
        }
    }

    private Pedido pagar(String cliente, String operacion) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                cliente + "@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        try {
            return servicio.realizarPedido("direccion-" + cliente, "metodo-" + cliente, operacion);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void dosClientesNoPuedenComprarLaUltimaUnidad() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> compraA = () -> intentarCompra("a", salida);
            Callable<Boolean> compraB = () -> intentarCompra("b", salida);
            var a = executor.submit(compraA);
            var b = executor.submit(compraB);
            salida.countDown();
            assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
        assertEquals(0, productos.findById("producto").orElseThrow().getStock());
        assertEquals(1, pedidos.count());
        assertEquals(1, carritos.findAll().stream().filter(c -> c.getProductos().isEmpty()).count());
    }

    private boolean intentarCompra(String cliente, CountDownLatch salida) throws Exception {
        salida.await(5, TimeUnit.SECONDS);
        try {
            pagar(cliente, UUID.randomUUID().toString());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Test
    void pagoRepetidoYSnapshotTrasEliminarProductoYVendedor() {
        String operacion = UUID.randomUUID().toString();
        Pedido pedido = pagar("a", operacion);
        assertEquals(pedido.getId(), pagar("a", operacion).getId());
        productos.deleteById("producto");
        template.remove(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("id")
                        .is(pedido.getLineas().getFirst().vendedorId())), Vendedor.class);
        Pedido historico = pedidos.findById(pedido.getId()).orElseThrow();
        assertEquals("Original", historico.getLineas().getFirst().nombreProducto());
        assertEquals(new java.math.BigDecimal("100.00"), historico.getLineas().getFirst().precioCompra());
        assertEquals("Tienda", historico.getLineas().getFirst().nombreVendedor());
        assertEquals(1, pedidos.count());
    }

    @Test
    void errorPosteriorRevierteStockPedidoYCarrito() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status -> {
            pagar("a", UUID.randomUUID().toString());
            throw new IllegalStateException("Fallo después de guardar y vaciar");
        }));
        assertEquals(1, productos.findById("producto").orElseThrow().getStock());
        assertEquals(0, pedidos.count());
        assertEquals(1, carritos.findById("a").orElseThrow().cantidadDe("producto"));
    }

    @Test
    void edicionAntiguaNoSobrescribeStockYDesactivarNoLoRestaura() {
        Producto antiguo = productos.findById("producto").orElseThrow();
        pagar("a", UUID.randomUUID().toString());
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> productos.actualizarDatos(antiguo, 1));
        productos.actualizarVisibilidad("producto", false);
        assertEquals(0, productos.findById("producto").orElseThrow().getStock());
    }
}
