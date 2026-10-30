package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.CarritoResponse;
import es.uclm.esibuy.backend.dto.LineaCarritoResponse;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.services.CarritoService;

@WebMvcTest(CarritoController.class)
@Import(SecurityConfiguration.class)
class CarritoControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private CarritoService servicio;

    @Test
    void devuelveCarritoActualizado() throws Exception {
        when(servicio.anadirProductoConImporte("p1", 3)).thenReturn(new CarritoResponse(
                List.of(new LineaCarritoResponse("p1", 3, new BigDecimal("8.00"), new BigDecimal("24.00"))),
                new BigDecimal("24.00")));
        mvc.perform(post("/cliente/carrito/productos").with(user("cliente@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productoId\":\"p1\",\"cantidad\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importe").value(24))
                .andExpect(jsonPath("$.productos[0].cantidad").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"productoId\":\"p1\",\"cantidad\":0}",
            "{\"productoId\":\"p1\",\"cantidad\":-1}", "{\"productoId\":\"\",\"cantidad\":1}"})
    void validaPeticion(String contenido) throws Exception {
        mvc.perform(post("/cliente/carrito/productos").with(user("cliente@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void mantieneProteccionCsrfYRol() throws Exception {
        mvc.perform(post("/cliente/carrito/productos").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cliente/carrito/productos").with(user("vendedor@example.com").roles("VENDEDOR"))
                .with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void devuelveMotivoDeErroresFuncionales() throws Exception {
        when(servicio.anadirProductoConImporte("p1", 3))
                .thenThrow(new IllegalArgumentException("No hay stock suficiente."))
                .thenThrow(new ProductoNoEncontradoException())
                .thenThrow(new AccessDeniedException("Producto exclusivo Premium."));
        int[] estados = {400, 404, 403};
        for (int estado : estados) {
            mvc.perform(post("/cliente/carrito/productos").with(user("cliente@example.com").roles("CLIENTE"))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productoId\":\"p1\",\"cantidad\":3}"))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.error").isNotEmpty());
        }
    }
}
