package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.model.Valoracion;
import es.uclm.esibuy.backend.services.PedidoService;
import es.uclm.esibuy.backend.services.ValoracionService;

@WebMvcTest(PedidoController.class)
@Import(SecurityConfiguration.class)
class ValoracionControllerTests {
    private static final String EMAIL_CLIENTE = "cliente@example.com";
    private static final String PEDIDO_ID = "pedido-1";
    private static final String PRODUCTO_ID = "producto-1";
    private static final String RUTA = "/cliente/pedidos/" + PEDIDO_ID
            + "/productos/" + PRODUCTO_ID + "/valoraciones";
    private static final String CUERPO = """
            {
              "puntuacion": 5,
              "comentario": "Buen producto"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private ValoracionService valoracionService;

    @Test
    void clienteAutenticadoPuedeValorarUnProductoDeSuPedido() throws Exception {
        when(valoracionService.valorar(
                EMAIL_CLIENTE,
                PEDIDO_ID,
                PRODUCTO_ID,
                5,
                "Buen producto"))
                .thenReturn(new Valoracion(
                        "valoracion-1",
                        "cliente-1",
                        PEDIDO_ID,
                        PRODUCTO_ID,
                        5,
                        "Buen producto"));

        mvc.perform(post(RUTA)
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("valoracion-1"))
                .andExpect(jsonPath("$.productoId").value(PRODUCTO_ID))
                .andExpect(jsonPath("$.puntuacion").value(5))
                .andExpect(jsonPath("$.comentario").value("Buen producto"))
                .andExpect(jsonPath("$.clienteId").doesNotExist());

        verify(valoracionService).valorar(
                EMAIL_CLIENTE,
                PEDIDO_ID,
                PRODUCTO_ID,
                5,
                "Buen producto");
    }

    @Test
    void valorarExigeSesionYTokenCsrf() throws Exception {
        mvc.perform(post(RUTA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().is4xxClientError());

        mvc.perform(post(RUTA)
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(valoracionService);
    }

    @Test
    void soloPermiteValorarAClientes() throws Exception {
        mvc.perform(post(RUTA)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        mvc.perform(post(RUTA)
                        .with(user("vendedor@example.com").roles("VENDEDOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(valoracionService);
    }
}
