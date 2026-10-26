package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ConsultarProductosResponse;
import es.uclm.esibuy.backend.dto.ModificarProductoRequest;
import es.uclm.esibuy.backend.exceptions.InvalidProductException;
import es.uclm.esibuy.backend.exceptions.ProductoNoEncontradoException;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.VendedorService;

@WebMvcTest(controllers = VendedorController.class)
@Import(SecurityConfiguration.class)
class ProductoControllerHU18Tests {

    private static final String EMAIL_VENDEDOR = "vendedor@example.com";
    private static final String URL_PRODUCTO = "/vendedor/productos/producto-1";
    private static final String JSON_VALIDO = """
            {"nombre":"Teclado mecánico","categoria":"Periféricos","descripcion":"Retroiluminado",
             "precio":59.99,"stock":10,"descuento":15,"descuentoPremium":20}
            """;
    private static final ModificarProductoRequest REQUEST_VALIDO = new ModificarProductoRequest(
            "Teclado mecánico", "Periféricos", "Retroiluminado", 59.99, 10, 15, 20);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private VendedorService vendedorService;

    @MockitoBean
    private SesionService sesionService;

    @Test
    void vendedorModificaSuProductoYRecibeElProductoActualizado() throws Exception {
        when(productoService.modificarProducto(EMAIL_VENDEDOR, "producto-1", REQUEST_VALIDO, null))
                .thenReturn(new ConsultarProductosResponse(
                        "producto-1",
                        "Teclado mecánico",
                        "Periféricos",
                        10,
                        "Retroiluminado",
                        59.99,
                        15,
                        20,
                        true,
                        null));

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("producto-1"))
                .andExpect(jsonPath("$.nombre").value("Teclado mecánico"))
                .andExpect(jsonPath("$.precio").value(59.99));
    }

    @Test
    void vendedorPuedeEnviarUnaImagenNueva() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen", "teclado.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .file(imagen)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(productoService).modificarProducto(eq(EMAIL_VENDEDOR), eq("producto-1"), eq(REQUEST_VALIDO), notNull());
    }

    @Test
    void imagenNuevaInvalidaDevuelveBadRequestConMensaje() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen", "falsa.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });
        when(productoService.modificarProducto(eq(EMAIL_VENDEDOR), eq("producto-1"), any(), notNull()))
                .thenThrow(new IllegalArgumentException("La imagen debe ser PNG, JPG o GIF y no superar 5 MB."));

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .file(imagen)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La imagen debe ser PNG, JPG o GIF y no superar 5 MB."));
    }

    @Test
    void precioNoPositivoDevuelveBadRequestSinLlamarAlServicio() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO.replace("59.99", "0"))
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precio[0]").value("El precio debe ser mayor que 0"));

        verifyNoInteractions(productoService);
    }

    @Test
    void stockNegativoDevuelveBadRequestSinLlamarAlServicio() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO.replace("\"stock\":10", "\"stock\":-1"))
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.stock[0]").value("El stock no puede ser negativo"));

        verifyNoInteractions(productoService);
    }

    @Test
    void descuentoMayorQueCienDevuelveBadRequestSinLlamarAlServicio() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO.replace("\"descuento\":15", "\"descuento\":101"))
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.descuento[0]").value("El descuento no puede superar el 100 %"));

        verifyNoInteractions(productoService);
    }

    @Test
    void categoriaInexistenteDevuelveBadRequestConMensaje() throws Exception {
        when(productoService.modificarProducto(eq(EMAIL_VENDEDOR), eq("producto-1"), any(), isNull()))
                .thenThrow(new InvalidProductException());

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Los datos del producto no cumplen con los requisitos."));
    }

    @Test
    void productoDeOtroVendedorDevuelveNotFound() throws Exception {
        when(productoService.modificarProducto(eq(EMAIL_VENDEDOR), eq("producto-1"), any(), isNull()))
                .thenThrow(new ProductoNoEncontradoException());

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No se ha encontrado el producto solicitado."));
    }

    @Test
    void vendedorNoActivoRecibeForbidden() throws Exception {
        when(productoService.modificarProducto(eq(EMAIL_VENDEDOR), eq("producto-1"), any(), isNull()))
                .thenThrow(new AccessDeniedException("Solo un vendedor activo puede modificar sus productos."));

        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeModificarProductos() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user("cliente@example.com").roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void administradorNoPuedeModificarProductos() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user("admin@esibuy.es").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void modificarSinTokenCsrfEsRechazado() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void usuarioNoAutenticadoNoPuedeModificar() throws Exception {
        mockMvc.perform(peticionModificar(JSON_VALIDO)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    private MockMultipartHttpServletRequestBuilder peticionModificar(String json) {
        MockMultipartFile producto = new MockMultipartFile(
                "producto", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
        return multipart(HttpMethod.PUT, URL_PRODUCTO).file(producto);
    }
}
