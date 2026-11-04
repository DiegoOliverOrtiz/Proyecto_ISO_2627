package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.exceptions.ProductoAjenoException;
import es.uclm.esibuy.backend.exceptions.ProductoConPedidosEnCursoException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.services.PerfilService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.VendedorService;

@WebMvcTest(controllers = VendedorController.class)
@Import(SecurityConfiguration.class)
class ProductoControllerHU33Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String URL = "/vendedor/productos/producto-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private VendedorService vendedorService;

    @MockitoBean
    private PerfilService perfilService;

    @MockitoBean
    private SesionService sesionService;

    @Test
    void vendedorEliminaSuProductoYRecibeNoContent() throws Exception {
        mockMvc.perform(delete(URL)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productoService).eliminarProducto(EMAIL_VENDEDOR, "producto-1");
    }

    @Test
    void productoConPedidosEnCursoDevuelveConflictoConElMotivo() throws Exception {
        doThrow(new ProductoConPedidosEnCursoException())
                .when(productoService).eliminarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(delete(URL)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "No se puede eliminar el producto porque figura en pedidos en curso (pagados o enviados)."));
    }

    @Test
    void productoDeOtroVendedorDevuelveForbiddenConElMotivo() throws Exception {
        doThrow(new ProductoAjenoException())
                .when(productoService).eliminarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(delete(URL)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No puedes eliminar productos de otro vendedor."));
    }

    @Test
    void productoInexistenteDevuelveNotFound() throws Exception {
        doThrow(new ProductoNoEncontradoException())
                .when(productoService).eliminarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(delete(URL)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No se ha encontrado el producto solicitado."));
    }

    @Test
    void vendedorNoActivoRecibeForbidden() throws Exception {
        doThrow(new AccessDeniedException("Solo un vendedor activo puede eliminar sus productos."))
                .when(productoService).eliminarProducto(EMAIL_VENDEDOR, "producto-1");

        mockMvc.perform(delete(URL)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
