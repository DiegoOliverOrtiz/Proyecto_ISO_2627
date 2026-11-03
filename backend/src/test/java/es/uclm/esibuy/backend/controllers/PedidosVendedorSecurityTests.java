package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dao.PedidoDAO;
import es.uclm.esibuy.backend.dao.VendedorDAO;
import es.uclm.esibuy.backend.model.EstadoPedido;
import es.uclm.esibuy.backend.model.LineaPedido;
import es.uclm.esibuy.backend.model.Pedido;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.PedidosVendedorService;

// HU-32 (T-32.4): acceso entre vendedores y saltos de estado, con el servicio real y los DAO simulados.
@WebMvcTest(PedidosVendedorController.class)
@Import({SecurityConfiguration.class, PedidosVendedorService.class})
class PedidosVendedorSecurityTests {

    private static final String RUTA = "/vendedor/pedidos";
    private static final String CORREO_A = "vendedor-a@test.com";
    private static final String CORREO_B = "vendedor-b@test.com";
    private static final String VENDEDOR_A = "vendedor-a";
    private static final String VENDEDOR_B = "vendedor-b";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoDAO pedidoDAO;

    @MockitoBean
    private VendedorDAO vendedorDAO;

    @BeforeEach
    void prepararVendedores() {
        lenient().when(vendedorDAO.findByEmail(CORREO_A)).thenReturn(Optional.of(vendedor(VENDEDOR_A, CORREO_A)));
        lenient().when(vendedorDAO.findByEmail(CORREO_B)).thenReturn(Optional.of(vendedor(VENDEDOR_B, CORREO_B)));
    }

    private Vendedor vendedor(String id, String correo) {
        Vendedor vendedor = new Vendedor();
        vendedor.setId(id);
        vendedor.setEmail(correo);
        vendedor.setActivada(true);
        vendedor.setBloqueada(false);
        return vendedor;
    }

    private Pedido pedidoDe(String vendedorId, EstadoPedido estado) {
        Instant fecha = Instant.parse("2026-11-03T10:00:00Z");
        return new Pedido(
                "pedido-1",
                "cliente-1",
                List.of(new LineaPedido("producto-1", "Producto", new BigDecimal("10.00"), 1,
                        vendedorId, "Tienda")),
                estado,
                new BigDecimal("10.00"),
                fecha,
                estado == EstadoPedido.EN_PROCESO ? null : fecha,
                null,
                "metodo-pago");
    }

    @Test
    void vendedorBNoPuedeAceptarElPedidoDelVendedorA() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.PAGADO_COMPLETADO)));

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_B).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.productos").doesNotExist());

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any());
    }

    @Test
    void elPedidoAjenoYElInexistenteResponden404Igual() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.PAGADO_COMPLETADO)));
        when(pedidoDAO.findById("no-existe")).thenReturn(Optional.empty());

        String ajeno = mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_B).roles("VENDEDOR")).with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String inexistente = mvc.perform(patch(RUTA + "/no-existe/aceptar")
                        .with(user(CORREO_B).roles("VENDEDOR")).with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals(inexistente, ajeno);
        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any());
    }

    @Test
    void elVendedorPropietarioSiPuedeAceptarlo() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.PAGADO_COMPLETADO)))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.TRAMITADO_ENVIADO)));
        when(pedidoDAO.actualizarEstado("pedido-1", "PAGADO_COMPLETADO", VENDEDOR_A, "TRAMITADO_ENVIADO"))
                .thenReturn(1L);

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TRAMITADO_ENVIADO"));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"TRAMITADO_ENVIADO", "FINALIZADO"})
    void noSePermitenSaltosNiRetrocesosDeEstado(EstadoPedido estado) throws Exception {
        when(pedidoDAO.findById("pedido-1")).thenReturn(Optional.of(pedidoDe(VENDEDOR_A, estado)));

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any());
    }

    @Test
    void unPedidoSinPagarNoSePuedeTramitarNiSeRevela() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.EN_PROCESO)));

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any());
    }

    @Test
    void siOtraPeticionCambiaElPedidoAntesSeDevuelve409() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.PAGADO_COMPLETADO)));
        when(pedidoDAO.actualizarEstado(any(), any(), any(), any())).thenReturn(0L);

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void sinCsrfSeRechazaLaAceptacion() throws Exception {
        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void sinSesionSeRechazaLaAceptacion() throws Exception {
        // SecurityConfiguration no define authenticationEntryPoint: el anónimo recibe 403, como en el resto de la API.
        mvc.perform(patch(RUTA + "/pedido-1/aceptar").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void clientesYAdministradoresNoPuedenAceptarPedidos() throws Exception {
        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user("cliente@test.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user("admin@test.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void vendedorBloqueadoRecibe403() throws Exception {
        Vendedor bloqueado = vendedor(VENDEDOR_A, CORREO_A);
        bloqueado.setBloqueada(true);
        when(vendedorDAO.findByEmail(CORREO_A)).thenReturn(Optional.of(bloqueado));

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .with(user(CORREO_A).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pedidoDAO);
    }

    @Test
    void elVendedorSiempreSeTomaDeLaSesion() throws Exception {
        when(pedidoDAO.findById("pedido-1"))
                .thenReturn(Optional.of(pedidoDe(VENDEDOR_A, EstadoPedido.PAGADO_COMPLETADO)));

        mvc.perform(patch(RUTA + "/pedido-1/aceptar")
                        .param("vendedorId", VENDEDOR_A)
                        .header("X-Vendedor-Id", VENDEDOR_A)
                        .with(user(CORREO_B).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(pedidoDAO, never()).actualizarEstado(any(), any(), any(), any());
    }
}
