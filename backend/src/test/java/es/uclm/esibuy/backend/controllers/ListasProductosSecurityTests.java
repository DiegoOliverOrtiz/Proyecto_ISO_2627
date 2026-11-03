package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.services.ListaProductosService;

// HU-39 (T-39.5): acceso a listas ajenas y CSRF en las mutaciones.
@WebMvcTest(ListasProductosController.class)
@Import(SecurityConfiguration.class)
class ListasProductosSecurityTests {

    private static final String RUTA = "/usuarios/listas";
    private static final String CORREO = "cliente@test.com";
    private static final String CUERPO = "{\"nombre\":\"Comida\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListaProductosService listaProductosService;

    static Stream<MockHttpServletRequestBuilder> mutaciones() {
        return Stream.of(
                post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO),
                put(RUTA + "/l1").contentType(MediaType.APPLICATION_JSON).content(CUERPO),
                delete(RUTA + "/l1"),
                put(RUTA + "/l1/productos/p1"),
                delete(RUTA + "/l1/productos/p1"));
    }

    static Stream<MockHttpServletRequestBuilder> todas() {
        return Stream.concat(Stream.of(get(RUTA), get(RUTA + "/l1")), mutaciones());
    }

    @ParameterizedTest
    @MethodSource("mutaciones")
    void lasMutacionesSinCsrfSonRechazadas(MockHttpServletRequestBuilder peticion) throws Exception {
        mvc.perform(peticion.with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listaProductosService);
    }

    @ParameterizedTest
    @MethodSource("todas")
    void sinSesionNoSeAccedeANingunEndpoint(MockHttpServletRequestBuilder peticion) throws Exception {
        mvc.perform(peticion.with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(listaProductosService);
    }

    @Test
    void consultarListaAjenaDevuelve404SinRevelarQueExiste() throws Exception {
        when(listaProductosService.consultarLista(CORREO, "lista-de-otro"))
                .thenThrow(new ListaNoEncontradaException());

        mvc.perform(get(RUTA + "/lista-de-otro").with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No se ha encontrado la lista solicitada."));
    }

    @Test
    void modificarListaAjenaDevuelve404() throws Exception {
        when(listaProductosService.renombrar(eq(CORREO), eq("lista-de-otro"), anyString()))
                .thenThrow(new ListaNoEncontradaException());
        doThrow(new ListaNoEncontradaException())
                .when(listaProductosService).eliminar(CORREO, "lista-de-otro");
        when(listaProductosService.anadirProducto(CORREO, "lista-de-otro", "p1"))
                .thenThrow(new ListaNoEncontradaException());
        when(listaProductosService.quitarProducto(CORREO, "lista-de-otro", "p1"))
                .thenThrow(new ListaNoEncontradaException());

        mvc.perform(put(RUTA + "/lista-de-otro").with(user(CORREO).roles("CLIENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
        mvc.perform(delete(RUTA + "/lista-de-otro").with(user(CORREO).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(put(RUTA + "/lista-de-otro/productos/p1").with(user(CORREO).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(RUTA + "/lista-de-otro/productos/p1").with(user(CORREO).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void elPropietarioSiempreEsElUsuarioDeLaSesion() throws Exception {
        when(listaProductosService.crear(CORREO, "Comida"))
                .thenReturn(new ListaProductosResponse("l1", "Comida", false, List.of()));

        mvc.perform(post(RUTA)
                        .param("usuarioId", "victima")
                        .header("X-Usuario-Id", "victima")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Comida\",\"usuarioId\":\"victima\"}"))
                .andExpect(status().isCreated());

        verify(listaProductosService).crear(CORREO, "Comida");
        verify(listaProductosService, never()).crear(eq("victima"), anyString());
    }

    @Test
    void usuarioBloqueadoONoActivoRecibe403() throws Exception {
        when(listaProductosService.consultarListas(CORREO))
                .thenThrow(new AccessDeniedException("No se puede acceder a las listas."));

        mvc.perform(get(RUTA).with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }
}
