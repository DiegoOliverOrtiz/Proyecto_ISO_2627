package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionSystemException;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.services.PedidoService;

@WebMvcTest(PedidoController.class)
@Import(SecurityConfiguration.class)
class PedidoControllerTests {
    private static final String OPERACION = "0746e76d-70d6-49a8-b972-f08b359474e1";
    private static final String JSON = "{\"direccionId\":\"direccion-a\",\"metodoPagoId\":\"metodo-a\","
            + "\"operacionId\":\"" + OPERACION + "\"}";
    @Autowired private MockMvc mvc;
    @MockitoBean private PedidoService servicio;

    private MockHttpServletRequestBuilder peticion(String contenido) {
        return post("/cliente/pedidos").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(contenido);
    }

    private Pedido pedido() {
        Direccion direccion = new Direccion();
        direccion.setId("direccion-a");
        direccion.setCalle("Calle original");
        Instant fecha = Instant.parse("2026-11-01T10:00:00Z");
        return new Pedido("pedido-1", "cliente-a", List.of(new LineaPedido("p1", "Producto original",
                new BigDecimal("75.00"), 2, "v1", "Tienda")), EstadoPedido.PAGADO_COMPLETADO,
                new BigDecimal("150.00"), fecha, fecha, direccion, "metodo-a");
    }

    @Test
    void devuelve201ConResumenSinEntidadClienteNiDatosSensibles() throws Exception {
        when(servicio.realizarPedido("direccion-a", "metodo-a", OPERACION)).thenReturn(pedido());
        mvc.perform(peticion(JSON)).andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.estado").value("PAGADO_COMPLETADO"))
                .andExpect(jsonPath("$.importe").value(150))
                .andExpect(jsonPath("$.lineas[0].precioCompra").value(75))
                .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
                .andExpect(jsonPath("$.lineas[0].nombreProducto").value("Producto original"))
                .andExpect(jsonPath("$.direccionEnvio.calle").value("Calle original"))
                .andExpect(jsonPath("$.metodoPagoId").value("metodo-a"))
                .andExpect(jsonPath("$.fechaPago").isNotEmpty())
                .andExpect(jsonPath("$.clienteId").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.numeroTarjeta").doesNotExist())
                .andExpect(jsonPath("$.cvv").doesNotExist());
        verify(servicio).realizarPedido("direccion-a", "metodo-a", OPERACION);
    }

    @Test
    void ignoraPropietarioEImportesInyectadosYConservaClaveEnReenvios() throws Exception {
        when(servicio.realizarPedido("direccion-a", "metodo-a", OPERACION)).thenReturn(pedido());
        String manipulado = JSON.substring(0, JSON.length() - 1)
                + ",\"clienteId\":\"ajeno\",\"carritoId\":\"ajeno\",\"total\":1,\"precio\":1,"
                + "\"descuento\":100,\"stock\":999,\"estado\":\"FINALIZADO\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(peticion(manipulado)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("pedido-1"))
                    .andExpect(jsonPath("$.importe").value(150))
                    .andExpect(jsonPath("$.estado").value("PAGADO_COMPLETADO"));
        }
        verify(servicio, times(2)).realizarPedido("direccion-a", "metodo-a", OPERACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "[]", "{\"direccionId\":{\"$ne\":null}}",
            "{\"direccionId\":\"\",\"metodoPagoId\":\"m\",\"operacionId\":\"%s\"}",
            "{\"direccionId\":\"d\",\"metodoPagoId\":null,\"operacionId\":\"%s\"}",
            "{\"direccionId\":\"d\",\"metodoPagoId\":\"m\",\"operacionId\":\"incorrecto\"}"})
    void rechazaCuerpoInvalidoAntesDelServicio(String contenido) throws Exception {
        mvc.perform(peticion(contenido.replace("%s", OPERACION))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        verifyNoInteractions(servicio);
    }

    @Test
    void limitaLongitudDeIdentificadores() throws Exception {
        mvc.perform(peticion(JSON.replace("direccion-a", "a".repeat(129))))
                .andExpect(status().isBadRequest());
        mvc.perform(peticion(JSON.replace("metodo-a", "a".repeat(129))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Carrito vacío.", "Producto eliminado.", "No hay stock suficiente.",
            "La dirección no pertenece al cliente o no existe.", "El método de pago no pertenece al cliente o no existe."})
    void devuelveMotivoFuncionalCon400(String motivo) throws Exception {
        when(servicio.realizarPedido(anyString(), anyString(), anyString())).thenThrow(new IllegalArgumentException(motivo));
        mvc.perform(peticion(JSON)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(motivo));
    }

    @Test
    void cuentaNoAutorizadaRecibe403() throws Exception {
        when(servicio.realizarPedido(anyString(), anyString(), anyString()))
                .thenThrow(new AccessDeniedException("Detalle interno"));
        mvc.perform(peticion(JSON)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Solo un cliente activo puede realizar pedidos."));
    }

    @Test
    void conflictoDeCarritoOClaveDevuelve409() throws Exception {
        when(servicio.realizarPedido(anyString(), anyString(), anyString()))
                .thenThrow(new OptimisticLockingFailureException("Detalle interno"))
                .thenThrow(new DuplicateKeyException("Detalle interno"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(peticion(JSON)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(
                            "La compra ha cambiado o se está procesando. Revisa el carrito y repite con la misma clave de operación."));
        }
    }

    @Test
    void erroresDeInfraestructuraNoExponenDetalles() throws Exception {
        when(servicio.realizarPedido(anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("Credenciales y dirección interna"))
                .thenThrow(new TransactionSystemException("Detalle de la sesión Mongo"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(peticion(JSON)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value(
                            "No se ha podido completar la compra. Repite la petición con la misma clave de operación."));
        }
    }

    @Test
    void commitInciertoNoSePresentaComoPagoRechazado() throws Exception {
        MongoException error = new MongoException("Resultado de commit desconocido");
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        when(servicio.realizarPedido(anyString(), anyString(), anyString()))
                .thenThrow(new TransactionSystemException("Commit", error));
        mvc.perform(peticion(JSON)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        "No se ha podido confirmar el resultado del pago. Repite la petición con la misma clave de operación."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VENDEDOR", "USER"})
    void otrosRolesNoAcceden(String rol) throws Exception {
        mvc.perform(post("/cliente/pedidos").with(user("otro@example.com").roles(rol)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void exigeSesionYCsrfValido() throws Exception {
        mvc.perform(post("/cliente/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cliente/pedidos").with(user("a@example.com").roles("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andExpect(status().isForbidden());
        mvc.perform(post("/cliente/pedidos").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
