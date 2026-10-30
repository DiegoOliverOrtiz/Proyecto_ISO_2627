package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @Test
    void consultaCarritoSinRecibirPropietario() throws Exception {
        when(servicio.consultarCarrito()).thenReturn(new CarritoResponse(List.of(), new BigDecimal("0.00")));
        mvc.perform(get("/cliente/carrito").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productos").isEmpty())
                .andExpect(jsonPath("$.importe").value(0));
        verify(servicio).consultarCarrito();
    }

    @Test
    void modificaYEliminaConSesionYCsrf() throws Exception {
        var respuesta = new CarritoResponse(List.of(), new BigDecimal("0.00"));
        when(servicio.modificarCantidad("p1", 5)).thenReturn(respuesta);
        when(servicio.eliminarProducto("p1")).thenReturn(respuesta);
        mvc.perform(put("/cliente/carrito/productos/p1").with(user("cliente@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importe").value(0));
        mvc.perform(delete("/cliente/carrito/productos/p1").with(user("cliente@example.com").roles("CLIENTE"))
                .with(csrf())).andExpect(status().isOk());
        verify(servicio).modificarCantidad("p1", 5);
        verify(servicio).eliminarProducto("p1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"cantidad\":null}", "{\"cantidad\":0}",
            "{\"cantidad\":-1}", "{\"cantidad\":1.5}", "{\"cantidad\":2147483648}"})
    void validaNuevaCantidadAntesDeInvocarServicio(String contenido) throws Exception {
        mvc.perform(put("/cliente/carrito/productos/p1").with(user("cliente@example.com").roles("CLIENTE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void nuevasRutasMantienenRolSesionYCsrf() throws Exception {
        for (var peticion : List.of(get("/cliente/carrito"), put("/cliente/carrito/productos/p1"),
                delete("/cliente/carrito/productos/p1"))) {
            mvc.perform(peticion.with(user("vendedor@example.com").roles("VENDEDOR")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/cliente/carrito")).andExpect(status().isForbidden());
        mvc.perform(put("/cliente/carrito/productos/p1").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/cliente/carrito/productos/p1").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void devuelveDisponibilidadSinExponerPrecioDeLineaNoAdquirible() throws Exception {
        when(servicio.consultarCarrito()).thenReturn(new CarritoResponse(List.of(
                new LineaCarritoResponse("p1", 2, null, null, "Producto eliminado", null, false,
                        "El producto ha sido eliminado.")), new BigDecimal("0.00")));
        mvc.perform(get("/cliente/carrito").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productos[0].disponible").value(false))
                .andExpect(jsonPath("$.productos[0].aviso").value("El producto ha sido eliminado."))
                .andExpect(jsonPath("$.productos[0].precioUnitario").doesNotExist());
    }

    @Test
    void descartaCarritoConSesionYCsrf() throws Exception {
        when(servicio.descartarCarrito()).thenReturn(new CarritoResponse(List.of(), new BigDecimal("0.00")));
        mvc.perform(delete("/cliente/carrito").with(user("cliente@example.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productos").isEmpty())
                .andExpect(jsonPath("$.importe").value(0));
        verify(servicio).descartarCarrito();
    }

    @Test
    void descarteMantieneProteccionSesionRolYCsrf() throws Exception {
        mvc.perform(delete("/cliente/carrito").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/cliente/carrito").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        for (String rol : List.of("ADMIN", "VENDEDOR")) {
            mvc.perform(delete("/cliente/carrito").with(user("otro@example.com").roles(rol)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void descarteDevuelveConflictoSinDetallesInternos() throws Exception {
        when(servicio.descartarCarrito()).thenThrow(
                new org.springframework.dao.OptimisticLockingFailureException("detalle interno"));
        mvc.perform(delete("/cliente/carrito").with(user("cliente@example.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El carrito se ha modificado. Inténtalo de nuevo."));
    }
}
