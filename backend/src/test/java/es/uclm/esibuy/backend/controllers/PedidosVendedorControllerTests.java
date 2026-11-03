package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.LineaPedidoVendedorResponse;
import es.uclm.esibuy.backend.dto.PedidoVendedorResponse;
import es.uclm.esibuy.backend.exceptions.PedidoVendedorNoEncontradoException;
import es.uclm.esibuy.backend.exceptions.TransicionEstadoPedidoException;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.services.PedidosVendedorService;

@WebMvcTest(PedidosVendedorController.class)
@Import(SecurityConfiguration.class)
class PedidosVendedorControllerTests {

    private static final String RUTA = "/vendedor/pedidos";
    private static final String CORREO = "vendedor@test.com";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidosVendedorService servicio;

    private PedidoVendedorResponse pedido(
            String id,
            EstadoPedido estado) {

        return new PedidoVendedorResponse(
                id,
                "cliente eliminado",
                estado,
                Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-02T10:05:00Z"),
                List.of(new LineaPedidoVendedorResponse(
                        "producto-1",
                        "Producto de prueba",
                        new BigDecimal("12.50"),
                        2
                )),
                new BigDecimal("25.00"),
                null
        );
    }

    @Test
    void vendedorConsultaPedidosActivos() throws Exception {
        when(servicio.consultarActivos(CORREO))
                .thenReturn(List.of(
                        pedido("pedido-1", EstadoPedido.PAGADO_COMPLETADO)
                ));

        mvc.perform(get(RUTA + "/activos")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pedido-1"))
                .andExpect(jsonPath("$[0].clienteNombre").value("cliente eliminado"))
                .andExpect(jsonPath("$[0].estado")
                        .value("PAGADO_COMPLETADO"))
                .andExpect(jsonPath("$[0].productos[0].productoId")
                        .value("producto-1"))
                .andExpect(jsonPath("$[0].productos[0].cantidad")
                        .value(2))
                .andExpect(jsonPath("$[0].importeProductosPropios")
                        .value(25.00));

        verify(servicio).consultarActivos(CORREO);
    }

    @Test
    void vendedorConsultaHistorico() throws Exception {
        when(servicio.consultarHistorico(CORREO))
                .thenReturn(List.of(
                        pedido("pedido-2", EstadoPedido.FINALIZADO)
                ));

        mvc.perform(get(RUTA + "/historico")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pedido-2"))
                .andExpect(jsonPath("$[0].estado")
                        .value("FINALIZADO"));

        verify(servicio).consultarHistorico(CORREO);
    }

    @Test
    void vendedorConsultaDetallePropio() throws Exception {
        when(servicio.consultarDetalle(CORREO, "pedido-1"))
                .thenReturn(
                        pedido("pedido-1", EstadoPedido.PAGADO_COMPLETADO)
                );

        mvc.perform(get(RUTA + "/pedido-1")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.clienteNombre").value("cliente eliminado"))
                .andExpect(jsonPath("$.productos[0].nombreProducto")
                        .value("Producto de prueba"))
                .andExpect(jsonPath("$.importeProductosPropios")
                        .value(25.00));

        verify(servicio).consultarDetalle(CORREO, "pedido-1");
    }

    @Test
    void sinSesionNoPuedeConsultarActivos() throws Exception {
        mvc.perform(get(RUTA + "/activos"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(servicio);
    }

    @Test
    void sinSesionNoPuedeConsultarHistorico() throws Exception {
        mvc.perform(get(RUTA + "/historico"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(servicio);
    }

    @Test
    void sinSesionNoPuedeConsultarDetalle() throws Exception {
        mvc.perform(get(RUTA + "/pedido-1"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(servicio);
    }

    @Test
    void clienteNoPuedeConsultarPedidosDelVendedor() throws Exception {
        mvc.perform(get(RUTA + "/activos")
                        .with(user("cliente@test.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void administradorNoPuedeConsultarPedidosDelVendedor() throws Exception {
        mvc.perform(get(RUTA + "/historico")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void clienteNoPuedeConsultarDetalleDelVendedor() throws Exception {
        mvc.perform(get(RUTA + "/pedido-1")
                        .with(user("cliente@test.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }

    @Test
    void pedidoAjenoDevuelve404() throws Exception {
        when(servicio.consultarDetalle(CORREO, "pedido-ajeno"))
                .thenThrow(
                        new PedidoVendedorNoEncontradoException()
                );

        mvc.perform(get(RUTA + "/pedido-ajeno")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isNotFound());

        verify(servicio).consultarDetalle(CORREO, "pedido-ajeno");
    }

    @Test
    void noUtilizaIdentificadorDeVendedorManipulado() throws Exception {
        when(servicio.consultarActivos(CORREO))
                .thenReturn(List.of());

        mvc.perform(get(RUTA + "/activos")
                        .param("vendedorId", "otro-vendedor")
                        .header("X-Vendedor-Id", "otro-vendedor")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(servicio).consultarActivos(CORREO);
        verify(servicio, never()).consultarActivos("otro-vendedor");
    }

    @Test
    void vendedorSinPedidosRecibeListaVacia() throws Exception {
        when(servicio.consultarActivos(CORREO))
                .thenReturn(List.of());

        mvc.perform(get(RUTA + "/activos")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(servicio).consultarActivos(CORREO);
    }

    @Test
    void respuestaNoExponeIdentificadorMetodoPago() throws Exception {
        when(servicio.consultarDetalle(CORREO, "pedido-1"))
                .thenReturn(
                        pedido("pedido-1", EstadoPedido.PAGADO_COMPLETADO)
                );

        mvc.perform(get(RUTA + "/pedido-1")
                        .with(user(CORREO).roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodoPagoId").doesNotExist())
                .andExpect(jsonPath("$.clienteId").doesNotExist())
                .andExpect(jsonPath("$.importeTotal").doesNotExist());

        verify(servicio).consultarDetalle(CORREO, "pedido-1");
    }

    // HU-32: aceptar pedido

    @Test
    void vendedorAceptaPedidoPropio() throws Exception {
        when(servicio.aceptarPedido(CORREO, "pedido-1"))
                .thenReturn(
                        pedido("pedido-1", EstadoPedido.TRAMITADO_ENVIADO)
                );

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.estado").value("TRAMITADO_ENVIADO"));

        verify(servicio).aceptarPedido(CORREO, "pedido-1");
    }

    @Test
    void aceptarPedidoAjenoDevuelve404() throws Exception {
        when(servicio.aceptarPedido(CORREO, "pedido-ajeno"))
                .thenThrow(new PedidoVendedorNoEncontradoException());

        mvc.perform(patch(RUTA + "/pedido-ajeno/aceptar")
                        .with(user(CORREO).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aceptarPedidoEnEstadoNoPermitidoDevuelve409() throws Exception {
        when(servicio.aceptarPedido(CORREO, "pedido-enviado"))
                .thenThrow(new TransicionEstadoPedidoException());

        mvc.perform(patch(RUTA + "/pedido-enviado/aceptar")
                        .with(user(CORREO).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }
}
