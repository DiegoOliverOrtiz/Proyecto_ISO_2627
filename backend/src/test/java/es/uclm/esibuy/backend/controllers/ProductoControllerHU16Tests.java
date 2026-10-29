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
import es.uclm.esibuy.backend.dto.FiltroProductosRequest;
import es.uclm.esibuy.backend.dto.ProductoCatalogoResponse;
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
        when(productoService.consultarProductosParaClientes(
                EMAIL_CLIENTE, new FiltroProductosRequest(null, null, null, null, null)))
                .thenReturn(List.of(new ProductoCatalogoResponse(
                        "producto-1",
                        "Teclado",
                        "Informática",
                        4,
                        49.99,
                        49.99,
                        0,
                        0,
                        0,
                        "Descripción",
                        null,
                        "Tienda",
                        true,
                        false,
                        true)));

        mockMvc.perform(get("/cliente/productos")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("producto-1"))
                .andExpect(jsonPath("$[0].esVisible").value(true))
                .andExpect(jsonPath("$[0].precio").value(49.99));

        verify(productoService).consultarProductosParaClientes(
                EMAIL_CLIENTE, new FiltroProductosRequest(null, null, null, null, null));
    }

    @Test
    void catalogoAceptaFiltrosCombinadosEnElEndpointDeProductos() throws Exception {
        FiltroProductosRequest filtros = new FiltroProductosRequest("teclado.*", "Informática", 10.0, 50.0, true);
        when(productoService.consultarProductosParaClientes(EMAIL_CLIENTE, filtros))
                .thenReturn(List.of(new ProductoCatalogoResponse(
                        "producto-premium",
                        "Teclado",
                        "Informática",
                        4,
                        40.0,
                        50.0,
                        0,
                        20,
                        20,
                        null,
                        null,
                        "Tienda",
                        true,
                        true,
                        false)));

        mockMvc.perform(get("/cliente/productos")
                        .param("nombre", "teclado.*")
                        .param("categoria", "Informática")
                        .param("precioMinimo", "10.0")
                        .param("precioMaximo", "50.0")
                        .param("disponible", "true")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].soloPremium").value(true))
                .andExpect(jsonPath("$[0].puedeConsultarDetalle").value(false))
                .andExpect(jsonPath("$[0].precio").value(40.0));

        verify(productoService).consultarProductosParaClientes(EMAIL_CLIENTE, filtros);
    }

    @Test
    void rechazaPreciosNegativosSinConsultarElServicio() throws Exception {
        mockMvc.perform(get("/cliente/productos")
                        .param("precioMinimo", "-1")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoService);
    }

    @Test
    void rechazaRangoDePrecioInvertido() throws Exception {
        when(productoService.consultarProductosParaClientes(
                EMAIL_CLIENTE, new FiltroProductosRequest(null, null, 20.0, 10.0, null)))
                .thenThrow(new IllegalArgumentException("El precio mínimo no puede superar el precio máximo."));

        mockMvc.perform(get("/cliente/productos")
                        .param("precioMinimo", "20")
                        .param("precioMaximo", "10")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteInactivoRecibeForbiddenAlConsultarElCatalogo() throws Exception {
        when(productoService.consultarProductosParaClientes(
                EMAIL_CLIENTE, new FiltroProductosRequest(null, null, null, null, null)))
                .thenThrow(new AccessDeniedException("Solo un cliente activo puede consultar los productos."));

        mockMvc.perform(get("/cliente/productos")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteInactivoRecibeForbiddenAlConsultarElDetalle() throws Exception {
        when(productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, "producto-1"))
                .thenThrow(new AccessDeniedException("Solo un cliente activo puede consultar los productos."));

        mockMvc.perform(get("/cliente/productos/producto-1")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unClienteNormalNoPuedeConsultarElDetallePremiumPorApi() throws Exception {
        when(productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, "producto-premium"))
                .thenThrow(new AccessDeniedException("Este producto solo está disponible para clientes Premium."));

        mockMvc.perform(get("/cliente/productos/producto-premium")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void detalleDeProductoInexistenteODesactivadoDevuelve404() throws Exception {
        when(productoService.consultarDetalleParaCliente(EMAIL_CLIENTE, "producto-inactivo"))
                .thenThrow(new ProductoNoEncontradoException());

        mockMvc.perform(get("/cliente/productos/producto-inactivo")
                        .with(user(EMAIL_CLIENTE).roles("CLIENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void vendedorNoPuedeUsarLaConsultaDeClientes() throws Exception {
        mockMvc.perform(get("/cliente/productos")
                        .with(user(EMAIL_VENDEDOR).roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }
}
