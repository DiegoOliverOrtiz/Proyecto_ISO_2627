package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ProductoCatalogoResponse;
import es.uclm.esibuy.backend.dto.ValoracionPublicaResponse;
import es.uclm.esibuy.backend.dto.ValoracionesProductoResponse;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.ValoracionService;

@WebMvcTest(ValoracionesProductoController.class)
@Import(SecurityConfiguration.class)
class ValoracionesProductoControllerTests {
    private static final String EMAIL = "cliente@example.com";
    private static final String PRODUCTO_ID = "producto-1";
    private static final String RUTA = "/cliente/productos/" + PRODUCTO_ID + "/valoraciones";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private ValoracionService valoracionService;

    @Test
    void clienteConsultaMediaYValoracionesSinDatosDeAutor() throws Exception {
        when(productoService.consultarDetalleParaCliente(EMAIL, PRODUCTO_ID))
                .thenReturn(new ProductoCatalogoResponse(
                        PRODUCTO_ID, "Teclado", "Informática", 4,
                        25.0, 25.0, 0, 0, 0, "Descripción",
                        null, "Tienda", true, false, true));
        when(valoracionService.consultarValoracionesProducto(PRODUCTO_ID))
                .thenReturn(new ValoracionesProductoResponse(
                        4.0,
                        2,
                        List.of(
                                new ValoracionPublicaResponse(5, "Excelente"),
                                new ValoracionPublicaResponse(3, "Correcto"))));

        mvc.perform(get(RUTA).with(user(EMAIL).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media").value(4.0))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.valoraciones[0].puntuacion").value(5))
                .andExpect(jsonPath("$.valoraciones[0].comentario").value("Excelente"))
                .andExpect(jsonPath("$.valoraciones[0].clienteId").doesNotExist());

        verify(productoService).consultarDetalleParaCliente(EMAIL, PRODUCTO_ID);
        verify(valoracionService).consultarValoracionesProducto(PRODUCTO_ID);
    }

    @Test
    void consultaValoracionesRequiereSesionDeCliente() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().is4xxClientError());

        mvc.perform(get(RUTA).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService, valoracionService);
    }
}
