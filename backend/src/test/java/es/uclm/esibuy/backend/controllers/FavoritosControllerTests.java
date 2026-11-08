package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.FavoritosResponse;
import es.uclm.esibuy.backend.dto.ProductoFavoritoResponse;
import es.uclm.esibuy.backend.services.FavoritoService;

@WebMvcTest(FavoritosController.class)
@Import(SecurityConfiguration.class)
class FavoritosControllerTests {

    private static final String RUTA = "/usuarios/favoritos";
    private static final String CORREO = "cliente@test.com";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FavoritoService favoritoService;

    private FavoritosResponse respuestaVacia() {
        return new FavoritosResponse(List.of(), List.of());
    }

    @Test
    void consultarFavoritosDelAutenticado() throws Exception {
        when(favoritoService.consultar(CORREO))
                .thenReturn(respuestaVacia());

        mvc.perform(get(RUTA)
                        .with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids").isEmpty())
                .andExpect(jsonPath("$.productos").isEmpty());

        verify(favoritoService).consultar(CORREO);
    }

    @Test
    void consultarNoAceptaPropietarioManipulado() throws Exception {
        when(favoritoService.consultar(CORREO))
                .thenReturn(respuestaVacia());

        mvc.perform(get(RUTA)
                        .param("usuarioId", "otro-usuario")
                        .header("X-Usuario-Id", "otro-usuario")
                        .with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isOk());

        verify(favoritoService).consultar(CORREO);
        verify(favoritoService, never())
                .consultar("otro-usuario");
    }

    @Test
    void consultarExigeAutenticacion() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(favoritoService);
    }

    @Test
    void anadirConCsrfGuardaProducto() throws Exception {
        ProductoFavoritoResponse producto =
                new ProductoFavoritoResponse(
                        "producto-1",
                        "Producto de prueba",
                        null,
                        20.0,
                        10,
                        true,
                        null
                );

        FavoritosResponse respuesta = new FavoritosResponse(
                List.of("producto-1"),
                List.of(producto)
        );

        when(favoritoService.anadir(CORREO, "producto-1"))
                .thenReturn(respuesta);

        mvc.perform(put(RUTA + "/producto-1")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids[0]")
                        .value("producto-1"))
                .andExpect(jsonPath("$.productos[0].nombre")
                        .value("Producto de prueba"))
                .andExpect(jsonPath("$.productos[0].disponible")
                        .value(true));

        verify(favoritoService)
                .anadir(CORREO, "producto-1");
    }

    @Test
    void anadirSinCsrfEsRechazado() throws Exception {
        mvc.perform(put(RUTA + "/producto-1")
                        .with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(favoritoService);
    }

    @Test
    void quitarConCsrfEliminaFavorito() throws Exception {
        when(favoritoService.quitar(CORREO, "producto-1"))
                .thenReturn(respuestaVacia());

        mvc.perform(delete(RUTA + "/producto-1")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids").isEmpty());

        verify(favoritoService)
                .quitar(CORREO, "producto-1");
    }

    @Test
    void quitarSinCsrfEsRechazado() throws Exception {
        mvc.perform(delete(RUTA + "/producto-1")
                        .with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(favoritoService);
    }

    @Test
    void anadirSinSesionEsRechazado() throws Exception {
        mvc.perform(put(RUTA + "/producto-1")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(favoritoService);
    }

    @Test
    void quitarSinSesionEsRechazado() throws Exception {
        mvc.perform(delete(RUTA + "/producto-1")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(favoritoService);
    }

    @Test
    void noUtilizaPropietarioEnviadoPorElCliente() throws Exception {
        when(favoritoService.anadir(CORREO, "producto-1"))
                .thenReturn(respuestaVacia());

        mvc.perform(put(RUTA + "/producto-1")
                        .param("usuarioId", "victima")
                        .header("X-Usuario-Id", "victima")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(favoritoService)
                .anadir(CORREO, "producto-1");

        verify(favoritoService, never())
                .anadir(eq("victima"), anyString());
    }

    @Test
    void vendedorAutenticadoPuedeConsultarSuLista() throws Exception {
        when(favoritoService.consultar("vendedor@test.com"))
                .thenReturn(respuestaVacia());

        mvc.perform(get(RUTA)
                        .with(user("vendedor@test.com")
                                .roles("VENDEDOR")))
                .andExpect(status().isOk());

        verify(favoritoService)
                .consultar("vendedor@test.com");
    }
}
