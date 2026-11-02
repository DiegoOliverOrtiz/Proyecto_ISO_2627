package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    void productoAntiguoConCamposPredeterminadosAusentesPermitePagar() {
        Update update = new Update();
        for (String campo : List.of("descuento", "descuentoPremium", "soloPremium")) {
            update.unset(campo);
        }
        template.updateFirst(Query.query(Criteria.where("id").is("producto")), update, Producto.class);

        Pedido pedido = pagar("a", UUID.randomUUID().toString());

        assertEquals(EstadoPedido.PAGADO_COMPLETADO, pedido.getEstado());
        assertEquals(new BigDecimal("100.00"), pedido.getImporte());
        assertEquals(0, productos.findById("producto").orElseThrow().getStock());
        assertEquals(1, pedidos.count());
        assertTrue(carritos.findById("a").orElseThrow().getProductos().isEmpty());
    }

    @Test
    void cambioDeDescuentoPosteriorALaLecturaRechazaElDescuentoDeStock() {
        Producto anterior = productos.findById("producto").orElseThrow();
        template.updateFirst(Query.query(Criteria.where("id").is("producto")),
                new Update().set("descuentoPremium", 20), Producto.class);

        assertThrows(IllegalArgumentException.class, () -> productos.descontarStock(anterior, 1));
        assertEquals(1, productos.findById("producto").orElseThrow().getStock());
    }

    @Test
    void anadirModificarYConsultarNoDescuentaStockHastaPagar() {
        template.updateFirst(Query.query(Criteria.where("id").is("producto")),
                new Update().set("stock", 10), Producto.class);
        CarritoService carritoService = new CarritoService(carritos, clientes, productos);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "a@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));

        carritoService.anadirProducto("producto", 1);
        assertEquals(2, carritos.findById("a").orElseThrow().cantidadDe("producto"));
        assertEquals(10, productos.findById("producto").orElseThrow().getStock());
        carritoService.modificarCantidad("producto", 3);
        assertEquals(3, carritos.findById("a").orElseThrow().cantidadDe("producto"));
        assertEquals(10, productos.findById("producto").orElseThrow().getStock());
        carritoService.modificarCantidad("producto", 2);
        assertEquals(2, carritoService.consultarCarrito().productos().getFirst().cantidad());
        assertEquals(10, productos.findById("producto").orElseThrow().getStock());
        assertEquals(0, pedidos.count());

        Pedido pedido = pagar("a", UUID.randomUUID().toString());
        assertEquals(EstadoPedido.PAGADO_COMPLETADO, pedido.getEstado());
        assertEquals(8, productos.findById("producto").orElseThrow().getStock());
        assertTrue(carritos.findById("a").orElseThrow().getProductos().isEmpty());
    }

    @Test
    void stockInsuficienteConservaExistenciasCarritoYSinPedido() {
        carritos.save(carritos.findById("a").orElseThrow().conCantidad("producto", 2));
        Carrito anterior = carritos.findById("a").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> pagar("a", UUID.randomUUID().toString()));
        assertEquals(1, productos.findById("producto").orElseThrow().getStock());
        assertEquals(0, pedidos.count());
        Carrito conservado = carritos.findById("a").orElseThrow();
        assertEquals(anterior.getProductos(), conservado.getProductos());
        assertEquals(anterior.getVersion(), conservado.getVersion());
    }

    @Test
    void compraVariosProductosDescuentaCadaCantidadExacta() {
        prepararDosProductos();
        Pedido pedido = pagar("a", UUID.randomUUID().toString());
        assertEquals(8, productos.findById("producto").orElseThrow().getStock());
        assertEquals(2, productos.findById("producto-2").orElseThrow().getStock());
        assertEquals(new BigDecimal("260.00"), pedido.getImporte());
        assertEquals(2, pedido.getLineas().size());
        assertEquals(1, pedidos.count());
        assertTrue(carritos.findById("a").orElseThrow().getProductos().isEmpty());
    }

    @Test
    void falloSegundaLineaReviertePrimerDescuentoYPedido() {
        prepararDosProductos();
        Carrito anterior = carritos.findById("a").orElseThrow();
        // Fallo determinista entre validación y descuento: se ejecutan las escrituras
        // Mongo reales, incluida la primera línea, y la transacción debe revertirlas.
        ProductoActualizacionDAOImpl fragmento = new ProductoActualizacionDAOImpl(template) {
            @Override
            public void descontarStock(Producto producto, int cantidad) {
                if (producto.getId().equals("producto-2")) {
                    assertEquals(8, productos.findById("producto").orElseThrow().getStock());
                    assertEquals(1, pedidos.count());
                    template.updateFirst(Query.query(Criteria.where("id").is("producto-2")),
                            new Update().set("stock", 0), Producto.class);
                }
                super.descontarStock(producto, cantidad);
            }
        };
        MongoRepositoryFactory factory = new MongoRepositoryFactory(template);
        ProductoDAO daoConFallo = factory.getRepository(ProductoDAO.class, fragmento);
        servicio = new PedidoService(new CarritoService(carritos, clientes, daoConFallo), carritos, daoConFallo,
                metodos, factory.getRepository(VendedorDAO.class), pedidos, manager);

        assertThrows(IllegalArgumentException.class, () -> pagar("a", UUID.randomUUID().toString()));
        assertEquals(10, productos.findById("producto").orElseThrow().getStock());
        assertEquals(5, productos.findById("producto-2").orElseThrow().getStock());
        assertEquals(0, pedidos.count());
        Carrito conservado = carritos.findById("a").orElseThrow();
        assertEquals(anterior.getProductos(), conservado.getProductos());
        assertEquals(anterior.getVersion(), conservado.getVersion());
    }

    private void prepararDosProductos() {
        template.updateFirst(Query.query(Criteria.where("id").is("producto")),
                new Update().set("stock", 10), Producto.class);
        Producto segundo = new Producto();
        segundo.setId("producto-2");
        segundo.setNombre("Segundo");
        segundo.setPrecio(20.0);
        segundo.setStock(5);
        segundo.setEsVisible(true);
        segundo.setVendedor("Tienda");
        productos.save(segundo);
        carritos.save(carritos.findById("a").orElseThrow()
                .conCantidad("producto", 2).conCantidad("producto-2", 3));
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

    @Test
    void mismaClaveDeClientesDistintosNoPermiteAccederAlPedidoAjeno() {
        template.updateFirst(Query.query(Criteria.where("id").is("producto")),
                new Update().set("stock", 5), Producto.class);
        String operacion = UUID.randomUUID().toString();
        Pedido a = pagar("a", operacion);
        Pedido b = pagar("b", operacion);
        assertNotEquals(a.getId(), b.getId());
        assertEquals("a", a.getClienteId());
        assertEquals("b", b.getClienteId());
        assertEquals("metodo-b", b.getMetodoPagoId());
        assertEquals("direccion-b", b.getDireccionEnvio().getId());
        assertEquals(2, pedidos.count());
        assertEquals(3, productos.findById("producto").orElseThrow().getStock());
    }

    @Test
    void peticionesSimultaneasConMismaClaveSoloCobranUnaVez() throws Exception {
        template.updateFirst(Query.query(Criteria.where("id").is("producto")),
                new Update().set("stock", 5), Producto.class);
        String operacion = UUID.randomUUID().toString();
        CountDownLatch salida = new CountDownLatch(1);
        Callable<Boolean> intento = () -> {
            assertTrue(salida.await(5, TimeUnit.SECONDS));
            try {
                pagar("a", operacion);
                return true;
            } catch (org.springframework.dao.DuplicateKeyException
                    | org.springframework.dao.OptimisticLockingFailureException conflicto) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(intento);
            var b = executor.submit(intento);
            salida.countDown();
            boolean resultadoA = a.get(30, TimeUnit.SECONDS);
            boolean resultadoB = b.get(30, TimeUnit.SECONDS);
            assertTrue(resultadoA || resultadoB);
        }
        Pedido resultado = pagar("a", operacion);
        assertEquals(EstadoPedido.PAGADO_COMPLETADO, resultado.getEstado());
        assertEquals(1, pedidos.count());
        assertEquals(4, productos.findById("producto").orElseThrow().getStock());
        assertTrue(carritos.findById("a").orElseThrow().getProductos().isEmpty());
        assertEquals(1, carritos.findById("b").orElseThrow().cantidadDe("producto"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productoOcultoOEliminadoAntesDePagarNoModificaCarritoNiCreaPedido(boolean eliminado) {
        Carrito anterior = carritos.findById("a").orElseThrow();
        if (eliminado) productos.deleteById("producto");
        else productos.actualizarVisibilidad("producto", false);
        assertThrows(IllegalArgumentException.class, () -> pagar("a", UUID.randomUUID().toString()));
        assertEquals(0, pedidos.count());
        assertEquals(anterior.getProductos(), carritos.findById("a").orElseThrow().getProductos());
        if (!eliminado) assertEquals(1, productos.findById("producto").orElseThrow().getStock());
    }

    @Test
    void documentoPedidoNoCopiaTokenNiDatosDeTarjeta() {
        template.updateFirst(Query.query(Criteria.where("id").is("metodo-a")),
                new Update().set("token", "token-privado-hu26").set("referenciaEnmascarada", "****4242"),
                MetodoPago.class);
        Pedido pedido = pagar("a", UUID.randomUUID().toString());
        Document guardado = template.getCollection("pedidos").find(new Document("_id", pedido.getId())).first();
        assertNotNull(guardado);
        assertEquals("metodo-a", guardado.getString("metodoPagoId"));
        String json = guardado.toJson();
        for (String sensible : List.of("token-privado-hu26", "referenciaEnmascarada", "cvv", "numeroTarjeta")) {
            assertFalse(json.contains(sensible));
        }
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pagoRepetidoYSnapshotTrasCambiosYEliminaciones(boolean premium) {
        Producto producto = productos.findById("producto").orElseThrow();
        producto.setPrecio(123.45);
        producto.setDescuento(10);
        producto.setDescuentoPremium(25);
        producto.setStock(5);
        productos.save(producto);
        Cliente cliente = clientes.findById("a").orElseThrow();
        cliente.setEsPremium(premium);
        clientes.save(cliente);
        carritos.save(carritos.findById("a").orElseThrow().conCantidad("producto", 2));

        String operacion = UUID.randomUUID().toString();
        Pedido pedido = pagar("a", operacion);
        BigDecimal precioEsperado = new BigDecimal(premium ? "92.59" : "111.11");
        BigDecimal importeEsperado = new BigDecimal(premium ? "185.18" : "222.22");
        String vendedorId = pedido.getLineas().getFirst().vendedorId();
        assertNotNull(vendedorId);
        assertTrue(template.exists(Query.query(Criteria.where("id").is(vendedorId)), Vendedor.class));
        LineaPedido lineaEsperada = new LineaPedido("producto", "Original", precioEsperado,
                2, vendedorId, "Tienda");
        comprobarHistorico(pedido.getId(), lineaEsperada, importeEsperado);

        assertEquals(pedido.getId(), pagar("a", operacion).getId());
        assertEquals(3, productos.findById("producto").orElseThrow().getStock());

        producto = productos.findById("producto").orElseThrow();
        producto.setNombre("Nombre cambiado");
        producto.setPrecio(1.0);
        producto.setDescuento(0);
        producto.setDescuentoPremium(0);
        productos.save(producto);
        assertEquals(1, template.updateFirst(Query.query(Criteria.where("id").is(vendedorId)),
                new Update().set("nombreComercial", "Tienda cambiada"), Vendedor.class).getModifiedCount());
        assertEquals("Nombre cambiado", productos.findById("producto").orElseThrow().getNombre());
        assertEquals(1.0, productos.findById("producto").orElseThrow().getPrecio());
        comprobarHistorico(pedido.getId(), lineaEsperada, importeEsperado);

        productos.deleteById("producto");
        template.remove(Query.query(Criteria.where("id").is(vendedorId)), Vendedor.class);
        assertFalse(productos.existsById("producto"));
        assertFalse(template.exists(Query.query(Criteria.where("id").is(vendedorId)), Vendedor.class));
        comprobarHistorico(pedido.getId(), lineaEsperada, importeEsperado);
        assertEquals(1, pedidos.count());
    }

    private void comprobarHistorico(String pedidoId, LineaPedido lineaEsperada, BigDecimal importeEsperado) {
        Pedido historico = pedidos.findById(pedidoId).orElseThrow();
        assertEquals(List.of(lineaEsperada), historico.getLineas());
        assertEquals(importeEsperado, historico.getImporte());
        assertEquals(EstadoPedido.PAGADO_COMPLETADO, historico.getEstado());
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
