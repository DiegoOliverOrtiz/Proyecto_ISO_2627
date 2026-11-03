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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ListaProductosResponse;
import es.uclm.esibuy.backend.dto.ResumenListaProductosResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreListaDuplicadoException;
import es.uclm.esibuy.backend.exceptions.OperacionListaNoPermitidaException;
import es.uclm.esibuy.backend.services.ListaProductosService;

@WebMvcTest(ListasProductosController.class)
@Import(SecurityConfiguration.class)
class ListasProductosControllerTests {

    private static final String RUTA = "/usuarios/listas";
    private static final String CORREO = "cliente@test.com";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListaProductosService listaProductosService;

    private ListaProductosResponse lista(String id, String nombre) {
        return new ListaProductosResponse(id, nombre, false, List.of());
    }

    @Test
    void consultarListasDelAutenticado() throws Exception {
        when(listaProductosService.consultarListas(CORREO)).thenReturn(List.of(
                new ResumenListaProductosResponse("favoritos:1", "Favoritos", true, List.of("p1")),
                new ResumenListaProductosResponse("l1", "Comida", false, List.of())));

        mvc.perform(get(RUTA).with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Favoritos"))
                .andExpect(jsonPath("$[0].predeterminada").value(true))
                .andExpect(jsonPath("$[0].productoIds[0]").value("p1"))
                .andExpect(jsonPath("$[1].nombre").value("Comida"));
    }

    @Test
    void consultarUnaListaPropia() throws Exception {
        when(listaProductosService.consultarLista(CORREO, "l1")).thenReturn(lista("l1", "Comida"));

        mvc.perform(get(RUTA + "/l1").with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("l1"))
                .andExpect(jsonPath("$.productos").isEmpty());
    }

    @Test
    void consultarListaNoEncontradaDevuelve404() throws Exception {
        when(listaProductosService.consultarLista(CORREO, "ajena"))
                .thenThrow(new ListaNoEncontradaException());

        mvc.perform(get(RUTA + "/ajena").with(user(CORREO).roles("CLIENTE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No se ha encontrado la lista solicitada."));
    }

    @Test
    void crearListaDevuelve201() throws Exception {
        when(listaProductosService.crear(CORREO, "Videojuegos")).thenReturn(lista("l1", "Videojuegos"));

        mvc.perform(post(RUTA)
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Videojuegos\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Videojuegos"));
    }

    @Test
    void crearConNombreVacioDevuelve400() throws Exception {
        mvc.perform(post(RUTA)
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listaProductosService);
    }

    @Test
    void crearConNombreDeMasDeCincuentaCaracteresDevuelve400() throws Exception {
        mvc.perform(post(RUTA)
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listaProductosService);
    }

    @Test
    void crearConNombreRepetidoDevuelve409() throws Exception {
        when(listaProductosService.crear(CORREO, "Comida"))
                .thenThrow(new NombreListaDuplicadoException());

        mvc.perform(post(RUTA)
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Comida\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya tienes una lista con ese nombre."));
    }

    @Test
    void renombrarLista() throws Exception {
        when(listaProductosService.renombrar(CORREO, "l1", "Alimentación"))
                .thenReturn(lista("l1", "Alimentación"));

        mvc.perform(put(RUTA + "/l1")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Alimentación\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Alimentación"));
    }

    @Test
    void renombrarFavoritosDevuelve409() throws Exception {
        when(listaProductosService.renombrar(eq(CORREO), eq("favoritos:1"), anyString()))
                .thenThrow(new OperacionListaNoPermitidaException(
                        "La lista de favoritos no se puede renombrar."));

        mvc.perform(put(RUTA + "/favoritos:1")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otra\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminarListaDevuelve204() throws Exception {
        mvc.perform(delete(RUTA + "/l1")
                        .with(user(CORREO).roles("CLIENTE"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(listaProductosService).eliminar(CORREO, "l1");
    }

    @Test
    void vendedorTambienTieneListas() throws Exception {
        when(listaProductosService.consultarListas("vendedor@test.com")).thenReturn(List.of());

        mvc.perform(get(RUTA).with(user("vendedor@test.com").roles("VENDEDOR")))
                .andExpect(status().isOk());

        verify(listaProductosService).consultarListas("vendedor@test.com");
    }
}
