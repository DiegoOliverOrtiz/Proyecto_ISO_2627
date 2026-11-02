package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.SimpleTransactionStatus;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.*;
import es.uclm.esibuy.backend.model.*;
import es.uclm.esibuy.backend.services.CarritoService;
import es.uclm.esibuy.backend.services.PedidoService;

@WebMvcTest(PedidoController.class)
@Import({SecurityConfiguration.class, CarritoService.class, PedidoService.class})
class PedidoControllerSecurityTests {
    private static final String JSON = "{\"direccionId\":\"direccion-a\",\"metodoPagoId\":\"metodo-a\","
            + "\"operacionId\":\"0746e76d-70d6-49a8-b972-f08b359474e1\"}";
    @Autowired private MockMvc mvc;
    @MockitoBean private ClienteDAO clientes;
    @MockitoBean private CarritoDAO carritos;
    @MockitoBean private ProductoDAO productos;
    @MockitoBean private MetodoPagoDAO metodos;
    @MockitoBean private VendedorDAO vendedores;
    @MockitoBean private PedidoDAO pedidos;
    @MockitoBean private MongoTransactionManager transacciones;
    private Cliente cliente;
    private Map<String, Carrito> datosCarritos;
    private Carrito carritoAjeno;

    @BeforeEach
    void configurar() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(true);
        Direccion direccion = new Direccion();
        direccion.setId("direccion-a");
        direccion.setCalle("Calle A");
        cliente.agregarDireccion(direccion);
        when(clientes.findByEmail("a@example.com")).thenReturn(Optional.of(cliente));
        datosCarritos = new HashMap<>();
        datosCarritos.put("cliente-a", new Carrito("cliente-a").conCantidad("p1", 2));
        carritoAjeno = new Carrito("cliente-b").conCantidad("p1", 9);
        datosCarritos.put("cliente-b", carritoAjeno);
        when(carritos.findById(anyString())).thenAnswer(i -> Optional.ofNullable(datosCarritos.get(i.getArgument(0))));
        when(carritos.save(any())).thenAnswer(i -> {
            Carrito carrito = i.getArgument(0);
            datosCarritos.put(carrito.getId(), carrito);
            return carrito;
        });
        Producto producto = new Producto();
        producto.setId("p1");
        producto.setNombre("Producto original");
        producto.setPrecio(100.0);
        producto.setDescuento(10);
        producto.setStock(10);
        producto.setEsVisible(true);
        producto.setVendedor("Tienda");
        when(productos.findById("p1")).thenReturn(Optional.of(producto));
        MetodoPago metodo = new MetodoPago();
        metodo.setId("metodo-a");
        metodo.setClienteId("cliente-a");
        metodo.setToken("token-que-no-debe-devolverse");
        when(metodos.findByClienteId("cliente-a")).thenReturn(List.of(metodo));
        Vendedor vendedor = new Vendedor();
        vendedor.setId("v1");
        vendedor.setNombreComercial("Tienda");
        when(vendedores.findByNombreComercial("Tienda")).thenReturn(Optional.of(vendedor));
        AtomicReference<Pedido> persistido = new AtomicReference<>();
        when(pedidos.findById(anyString())).thenAnswer(i -> Optional.ofNullable(persistido.get()));
        when(pedidos.insert(any(Pedido.class))).thenAnswer(i -> { persistido.set(i.getArgument(0)); return persistido.get(); });
        when(pedidos.save(any(Pedido.class))).thenAnswer(i -> { persistido.set(i.getArgument(0)); return persistido.get(); });
    }

    private MockHttpServletRequestBuilder peticion(String contenido) {
        return post("/cliente/pedidos").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(contenido);
    }

    @Test
    void manipularPropietarioYPreciosNoCambiaLaCompraReal() throws Exception {
        String contenido = JSON.substring(0, JSON.length() - 1)
                + ",\"clienteId\":\"cliente-b\",\"carritoId\":\"cliente-b\",\"precio\":1,\"total\":1,"
                + "\"subtotal\":1,\"importe\":1,\"descuento\":100,\"stock\":999,\"estado\":\"FINALIZADO\"}";
        String respuesta = mvc.perform(peticion(contenido).param("clienteId", "cliente-b")
                        .header("X-Cliente-Id", "cliente-b"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importe").value(180))
                .andExpect(jsonPath("$.lineas[0].precioCompra").value(90))
                .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
                .andExpect(jsonPath("$.estado").value("PAGADO_COMPLETADO"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains("token-que-no-debe-devolverse"));
        assertSame(carritoAjeno, datosCarritos.get("cliente-b"));
        assertTrue(datosCarritos.get("cliente-a").getProductos().isEmpty());
        verify(clientes, never()).findById("cliente-b");
        verify(carritos, never()).findById("cliente-b");
        verify(metodos, never()).findByClienteId("cliente-b");
        verify(productos).descontarStock(any(), eq(2));
        verify(pedidos).save(argThat(p -> "cliente-a".equals(p.getClienteId())));
    }

    @Test
    void claveYaPagadaConOtraSeleccionDevuelveConflictoSinSegundaCompra() throws Exception {
        mvc.perform(peticion(JSON)).andExpect(status().isCreated());
        mvc.perform(peticion(JSON.replace("direccion-a", "direccion-b"))).andExpect(status().isConflict());
        verify(productos, times(1)).descontarStock(any(), eq(2));
        verify(pedidos, times(1)).insert(any(Pedido.class));
        verify(carritos, times(1)).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"direccionId", "metodoPagoId", "operacionId"})
    void identificadoresConObjetosNoSeInterpretanComoConsultas(String campo) throws Exception {
        String valor = switch (campo) {
            case "direccionId" -> "direccion-a";
            case "metodoPagoId" -> "metodo-a";
            default -> "0746e76d-70d6-49a8-b972-f08b359474e1";
        };
        mvc.perform(peticion(JSON.replace("\"" + valor + "\"", "{\"$ne\":null}")))
                .andExpect(status().isBadRequest());
        verificarSinEscrituras();
    }

    @Test
    void repetirPeticionDevuelveMismoPedidoSinSegundaEscritura() throws Exception {
        String primera = mvc.perform(peticion(JSON)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String segunda = mvc.perform(peticion(JSON)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(primera, segunda);
        verify(productos, times(1)).descontarStock(any(), eq(2));
        verify(carritos, times(1)).save(any());
        verify(pedidos, times(1)).insert(any(Pedido.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"direccion-b", "inexistente"})
    void direccionNoPropiaSeRechazaSinEscribir(String direccion) throws Exception {
        mvc.perform(peticion(JSON.replace("direccion-a", direccion))).andExpect(status().isBadRequest());
        verificarSinEscrituras();
    }

    @ParameterizedTest
    @ValueSource(strings = {"metodo-b", "inexistente"})
    void metodoNoPropioSeRechazaSinEscribir(String metodo) throws Exception {
        mvc.perform(peticion(JSON.replace("metodo-a", metodo))).andExpect(status().isBadRequest());
        verificarSinEscrituras();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactivo", "bloqueado", "inexistente"})
    void cuentaNoActivaSeRevalidaEnServicio(String estado) throws Exception {
        cliente.setActivada(!estado.equals("inactivo"));
        cliente.setBloqueada(estado.equals("bloqueado"));
        if (estado.equals("inexistente")) when(clientes.findByEmail("a@example.com")).thenReturn(Optional.empty());
        mvc.perform(peticion(JSON)).andExpect(status().isForbidden());
        verificarSinEscrituras();
        verifyNoInteractions(carritos, metodos, productos);
    }

    private void verificarSinEscrituras() {
        verify(productos, never()).descontarStock(any(), anyInt());
        verify(pedidos, never()).insert(any(Pedido.class));
        verify(pedidos, never()).save(any());
        verify(carritos, never()).save(any());
    }
}
