package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.CarritoDAO;
import es.uclm.esibuy.backend.dao.ClienteDAO;
import es.uclm.esibuy.backend.dao.ProductoDAO;
import es.uclm.esibuy.backend.model.Carrito;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Producto;
import es.uclm.esibuy.backend.services.CarritoService;

@WebMvcTest(CarritoController.class)
@Import({SecurityConfiguration.class, CarritoService.class})
class CarritoControllerSecurityTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private CarritoDAO carritoDAO;
    @MockitoBean private ClienteDAO clienteDAO;
    @MockitoBean private ProductoDAO productoDAO;

    private MockHttpServletRequestBuilder peticion(String operacion) {
        return switch (operacion) {
            case "consulta" -> get("/cliente/carrito");
            case "cantidad" -> put("/cliente/carrito/productos/p1")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":5,\"clienteId\":\"cliente-b\"}");
            case "eliminar" -> delete("/cliente/carrito/productos/p1");
            case "descartar" -> delete("/cliente/carrito");
            default -> throw new IllegalArgumentException(operacion);
        };
    }

    private Map<String, Carrito> prepararCarritos(boolean leerProducto) {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(true);
        when(clienteDAO.findByEmail("a@example.com")).thenReturn(Optional.of(cliente));
        Map<String, Carrito> carritos = new HashMap<>();
        carritos.put("cliente-a", new Carrito("cliente-a").conCantidad("p1", 2));
        carritos.put("cliente-b", new Carrito("cliente-b").conCantidad("p1", 9).conCantidad("solo-b", 1));
        when(carritoDAO.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(carritos.get(invocation.getArgument(0))));
        // GET no escribe; los otros escenarios comprueban el documento final guardado.
        lenient().when(carritoDAO.save(any(Carrito.class))).thenAnswer(invocation -> {
            Carrito carrito = invocation.getArgument(0);
            carritos.put(carrito.getId(), carrito);
            return carrito;
        });
        if (leerProducto) {
            Producto producto = new Producto();
            producto.setId("p1");
            producto.setNombre("Producto A");
            producto.setPrecio(10.0);
            producto.setStock(10);
            producto.setEsVisible(true);
            when(productoDAO.findById("p1")).thenReturn(Optional.of(producto));
        }
        return carritos;
    }

    @ParameterizedTest
    @ValueSource(strings = {"consulta", "cantidad", "eliminar", "descartar"})
    void propietarioInyectadoNoPermiteAccederNiModificarOtroCarrito(String operacion) throws Exception {
        Map<String, Carrito> carritos = prepararCarritos(operacion.equals("consulta") || operacion.equals("cantidad"));
        Carrito ajeno = carritos.get("cliente-b");
        mvc.perform(peticion(operacion).param("clienteId", "cliente-b").param("carritoId", "cliente-b")
                .header("X-Cliente-Id", "cliente-b")
                .with(user("a@example.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(operacion.equals("consulta") ? 20
                        : operacion.equals("cantidad") ? 50 : 0));
        assertSame(ajeno, carritos.get("cliente-b"));
        assertEquals(9, ajeno.cantidadDe("p1"));
        assertEquals(1, ajeno.cantidadDe("solo-b"));
        assertEquals(operacion.equals("consulta") ? 2 : operacion.equals("cantidad") ? 5 : 0,
                carritos.get("cliente-a").cantidadDe("p1"));
        verify(carritoDAO, never()).findById("cliente-b");
        verify(carritoDAO, never()).save(argThat(c -> c.getId().equals("cliente-b")));
        verify(productoDAO, never()).save(any());
        verify(productoDAO, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cantidad", "eliminar"})
    void identificadorDeLineaAjenaEsRechazadoSinCambiarCarritos(String operacion) throws Exception {
        Map<String, Carrito> carritos = prepararCarritos(false);
        Carrito propio = carritos.get("cliente-a");
        Carrito ajeno = carritos.get("cliente-b");
        var solicitud = operacion.equals("cantidad") ? put("/cliente/carrito/productos/solo-b")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1}")
                : delete("/cliente/carrito/productos/solo-b");
        mvc.perform(solicitud.with(user("a@example.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isNotFound());
        assertSame(propio, carritos.get("cliente-a"));
        assertSame(ajeno, carritos.get("cliente-b"));
        verify(carritoDAO, never()).save(any());
        verify(carritoDAO, never()).findById("cliente-b");
        verifyNoInteractions(productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"consulta", "cantidad", "eliminar", "descartar"})
    void sinSesionONuevoRolNoSeAccedeALosDatos(String operacion) throws Exception {
        mvc.perform(peticion(operacion).with(csrf())).andExpect(status().isForbidden());
        for (String rol : new String[] {"ADMIN", "VENDEDOR", "USER"}) {
            mvc.perform(peticion(operacion).with(user("otro@example.com").roles(rol)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(carritoDAO, clienteDAO, productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cantidad", "eliminar", "descartar"})
    void csrfAusenteOIncorrectoRechazadoAntesDeAccederALosDatos(String operacion) throws Exception {
        mvc.perform(peticion(operacion).with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(peticion(operacion).with(user("a@example.com").roles("CLIENTE"))
                .with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        verifyNoInteractions(carritoDAO, clienteDAO, productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bloqueado", "inactivo", "inexistente"})
    void unaSesionConRolClienteNoEvitaLaComprobacionDeCuenta(String estado) throws Exception {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-a");
        cliente.setActivada(!estado.equals("inactivo"));
        cliente.setBloqueada(estado.equals("bloqueado"));
        when(clienteDAO.findByEmail("a@example.com")).thenReturn(
                estado.equals("inexistente") ? Optional.empty() : Optional.of(cliente));
        for (String operacion : new String[] {"consulta", "cantidad", "eliminar", "descartar"}) {
            mvc.perform(peticion(operacion).with(user("a@example.com").roles("CLIENTE")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(carritoDAO, productoDAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "2147483648", "999999999999999999999999"})
    void cantidadesManipuladasPorApiNoLleganALaPersistencia(String cantidad) throws Exception {
        mvc.perform(put("/cliente/carrito/productos/p1").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":" + cantidad + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(carritoDAO, clienteDAO, productoDAO);
    }

    @ParameterizedTest
    @ValueSource(ints = {11, Integer.MAX_VALUE})
    void excesoDeStockPorApiConservaElCarrito(int cantidad) throws Exception {
        Map<String, Carrito> carritos = prepararCarritos(true);
        Carrito original = carritos.get("cliente-a");
        mvc.perform(put("/cliente/carrito/productos/p1").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":" + cantidad + "}"))
                .andExpect(status().isBadRequest());
        assertSame(original, carritos.get("cliente-a"));
        assertEquals(2, original.cantidadDe("p1"));
        verify(carritoDAO, never()).save(any());
        verify(productoDAO).findById("p1");
        verifyNoMoreInteractions(productoDAO);
    }
}
