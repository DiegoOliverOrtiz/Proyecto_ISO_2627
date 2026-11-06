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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.server.ResponseStatusException;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.CambioEstadoPedidoResponse;
import es.uclm.esibuy.backend.dto.PedidoConsultaResponse;
import es.uclm.esibuy.backend.exceptions.CancelacionPedidoException;
import es.uclm.esibuy.backend.model.Direccion;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.services.PedidoService;
import es.uclm.esibuy.backend.services.ValoracionService;

@WebMvcTest(PedidoController.class)
@Import(SecurityConfiguration.class)
class PedidoControllerTests {
    private static final String RUTA_CONFIRMACION =
            "/cliente/pedidos/pedido-1/confirmar-recepcion";
    private static final String OPERACION = "0746e76d-70d6-49a8-b972-f08b359474e1";
    private static final String JSON = "{\"direccionId\":\"direccion-a\",\"metodoPagoId\":\"metodo-a\","
            + "\"operacionId\":\"" + OPERACION + "\"}";
    @Autowired private MockMvc mvc;
    @MockitoBean private PedidoService servicio;
    @MockitoBean private ValoracionService valoracionService;

    private MockHttpServletRequestBuilder peticion(String contenido) {
        return post("/cliente/pedidos").with(user("a@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(contenido);
    }

    private MockHttpServletRequestBuilder peticionCancelacion(String pedidoId) {
        return post("/cliente/pedidos/" + pedidoId + "/cancelar")
                .with(user("a@example.com").roles("CLIENTE"))
                .with(csrf());
    }

    private PedidoConsultaResponse respuestaConsulta() {
        Instant fecha = Instant.parse("2026-11-01T10:00:00Z");
        return new PedidoConsultaResponse("pedido-1",
                List.of(new LineaPedido("p1", "Teclado", new BigDecimal("20.00"),
                        1, "v1", "Tienda")),
                EstadoPedido.PAGADO_COMPLETADO, new BigDecimal("20.00"),
                fecha, fecha, null,
                List.of(new CambioEstadoPedidoResponse(EstadoPedido.EN_PROCESO, fecha),
                        new CambioEstadoPedidoResponse(EstadoPedido.PAGADO_COMPLETADO, fecha.plusSeconds(60))));
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
    void consultaPedidosActivosDevuelveLineasEstadoEHistorial() throws Exception {
        when(servicio.consultarPedidosActivos()).thenReturn(List.of(respuestaConsulta()));

        mvc.perform(get("/cliente/pedidos/activos").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pedido-1"))
                .andExpect(jsonPath("$[0].estado").value("PAGADO_COMPLETADO"))
                .andExpect(jsonPath("$[0].lineas[0].nombreProducto").value("Teclado"))
                .andExpect(jsonPath("$[0].importe").value(20))
                .andExpect(jsonPath("$[0].historialEstados.length()").value(2))
                .andExpect(jsonPath("$[0].historialEstados[1].estado").value("PAGADO_COMPLETADO"))
                .andExpect(jsonPath("$[0].historialEstados[1].fecha").value("2026-11-01T10:01:00Z"));

        verify(servicio).consultarPedidosActivos();
    }

    @Test
    void consultaHistoricoSinPedidosDevuelveListaVacia() throws Exception {
        when(servicio.consultarHistoricoPedidos()).thenReturn(List.of());

        mvc.perform(get("/cliente/pedidos/historico").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void detalleDePedidoAjenoDevuelve404() throws Exception {
        when(servicio.consultarPedido("pedido-ajeno"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));

        mvc.perform(get("/cliente/pedidos/pedido-ajeno").with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelaPedidoConSesionYDevuelveSuEstadoActualizado() throws Exception {
        Instant fecha = Instant.parse("2026-11-04T10:02:00Z");
        PedidoConsultaResponse cancelado = new PedidoConsultaResponse(
                "pedido-1",
                List.of(new LineaPedido("p1", "Teclado", new BigDecimal("20.00"),
                        1, "v1", "Tienda")),
                EstadoPedido.CANCELADO,
                new BigDecimal("20.00"),
                fecha.minusSeconds(120),
                fecha.minusSeconds(60),
                null,
                List.of(new CambioEstadoPedidoResponse(
                        EstadoPedido.EN_PROCESO, fecha.minusSeconds(120)),
                        new CambioEstadoPedidoResponse(
                                EstadoPedido.PAGADO_COMPLETADO, fecha.minusSeconds(60)),
                        new CambioEstadoPedidoResponse(EstadoPedido.CANCELADO, fecha)));
        when(servicio.cancelarPedido("pedido-1")).thenReturn(cancelado);

        mvc.perform(peticionCancelacion("pedido-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.historialEstados[2].estado").value("CANCELADO"));

        verify(servicio).cancelarPedido("pedido-1");
    }

    @Test
    void rechazoDeCancelacionDevuelve409ConElMotivo() throws Exception {
        String motivo = "No se puede cancelar: el pedido ya ha sido enviado.";
        when(servicio.cancelarPedido("pedido-1"))
                .thenThrow(new CancelacionPedidoException(motivo));

        mvc.perform(peticionCancelacion("pedido-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(motivo));
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

    @Test
    void clienteConfirmaRecepcionConSesionYCrsfValido() throws Exception {
        Instant fecha = Instant.parse("2026-11-05T12:00:00Z");

        PedidoConsultaResponse finalizado = new PedidoConsultaResponse(
                "pedido-1",
                List.of(),
                EstadoPedido.FINALIZADO,
                new BigDecimal("20.00"),
                fecha.minusSeconds(7200),
                fecha.minusSeconds(3600),
                null,
                List.of(
                        new CambioEstadoPedidoResponse(
                                EstadoPedido.TRAMITADO_ENVIADO,
                                fecha.minusSeconds(1800)),
                        new CambioEstadoPedidoResponse(
                                EstadoPedido.FINALIZADO, fecha)));

        when(servicio.confirmarRecepcion("pedido-1"))
                .thenReturn(finalizado);

        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("a@example.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.historialEstados[1].estado")
                        .value("FINALIZADO"));

        verify(servicio).confirmarRecepcion("pedido-1");
    }

    @Test
    void confirmarRecepcionSinCsrfDevuelve403() throws Exception {
        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("a@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void confirmarRecepcionConCsrfIncorrectoDevuelve403() throws Exception {
        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("a@example.com").roles("CLIENTE"))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void confirmarRecepcionSinAutenticacionDevuelve403() throws Exception {
        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VENDEDOR", "ADMIN", "USER"})
    void soloLosClientesPuedenConfirmarRecepcion(String rol)
            throws Exception {

        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("otro@example.com").roles(rol))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void confirmarPedidoAjenoOInexistenteDevuelve404() throws Exception {
        when(servicio.confirmarRecepcion("pedido-1"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido no encontrado."));

        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("a@example.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(servicio).confirmarRecepcion("pedido-1");
    }

    @Test
    void confirmarPedidoEnEstadoIncorrectoDevuelve409() throws Exception {
        when(servicio.confirmarRecepcion("pedido-1"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Solo se puede confirmar la recepción de un pedido enviado."));

        mvc.perform(post(RUTA_CONFIRMACION)
                        .with(user("a@example.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(servicio).confirmarRecepcion("pedido-1");
    }
}
