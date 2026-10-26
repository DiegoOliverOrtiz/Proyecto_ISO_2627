package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.exceptions.EstadoProductoException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.services.ClienteService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.VendedorService;

@WebMvcTest(controllers = {
        VendedorController.class,
        ClienteController.class
})
@Import(SecurityConfiguration.class)
class ProductoControllerHU16Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String EMAIL_CLIENTE = "cliente@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private VendedorService vendedorService;

    @MockitoBean
    private ClienteService clienteService;

    @MockitoBean
    private SesionService sesionService;

    @Test
    void vendedorPuedeDesactivarSuProducto() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/desactivar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productoService).desactivarProducto(EMAIL_VENDEDOR, "producto-1");
    }

    @Test
    void vendedorPuedeActivarSuProducto() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/activar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productoService).activarProducto(EMAIL_VENDEDOR, "producto-1");
    }

    @Test
    void productoDeOtroVendedorDevuelveNotFound() throws Exception {
        doThrow(new ProductoNoEncontradoException())
                .when(productoService)
                .desactivarProducto(EMAIL_VENDEDOR, "producto-ajeno");

        mockMvc.perform(patch("/vendedor/productos/producto-ajeno/desactivar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No se ha encontrado el producto solicitado."));
    }

    @Test
    void estadoRepetidoDevuelveConflictoConMensaje() throws Exception {
        doThrow(new EstadoProductoException(true))
                .when(productoService)
                .activarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(patch("/vendedor/productos/producto-1/activar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El producto ya está activo."));
    }

    @Test
    void vendedorNoActivoRecibeForbidden() throws Exception {
        doThrow(new AccessDeniedException("Solo un vendedor activo puede cambiar el estado de sus productos."))
                .when(productoService)
                .desactivarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(patch("/vendedor/productos/producto-1/desactivar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeCambiarElEstadoDeUnProducto() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/desactivar")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void administradorNoPuedeCambiarElEstadoDeUnProducto() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/activar")
                        .with(user("admin@esibuy.es").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void cambiarEstadoSinTokenCsrfEsRechazado() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/desactivar")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void usuarioNoAutenticadoNoPuedeCambiarElEstado() throws Exception {
        mockMvc.perform(patch("/vendedor/productos/producto-1/desactivar")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void clienteConsultaLosProductosActivosConElEmailDeLaSesion() throws Exception {
        when(productoService.consultarProductosParaClientes(EMAIL_CLIENTE))
                .thenReturn(List.of(new ConsultarProductosResponse(
                        "producto-1",
                        "Teclado",
                        "Informática",
                        4,
                        null,
                        49.99,
                        0,
                        0,
                        true,
                        null,
                        false)));

        mockMvc.perform(get("/cliente/productos")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("producto-1"))
                .andExpect(jsonPath("$[0].esVisible").value(true));

        verify(productoService).consultarProductosParaClientes(EMAIL_CLIENTE);
    }

    @Test
    void vendedorNoPuedeUsarLaConsultaDeClientes() throws Exception {
        mockMvc.perform(get("/cliente/productos")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }
}
