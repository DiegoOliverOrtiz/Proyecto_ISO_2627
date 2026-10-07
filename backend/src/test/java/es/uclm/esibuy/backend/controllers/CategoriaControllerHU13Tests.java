package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.ModificarCategoriaRequest;
import es.uclm.esibuy.backend.exceptions.CategoriaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreCategoriaDuplicadoException;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.services.CategoriaService;

@WebMvcTest(controllers = CategoriaController.class)
@Import(SecurityConfiguration.class)
class CategoriaControllerHU13Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaService categoriaService;

    @Test
    void administradorPuedeModificarCategoria() throws Exception {
        Categoria modificada = new Categoria("Casa", "Artículos para el hogar");
        modificada.setId("categoria-1");

        when(categoriaService.modificarCategoria(
                eq("categoria-1"),
                any(ModificarCategoriaRequest.class)))
                .thenReturn(modificada);

        mockMvc.perform(put("/categorias/categoria-1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Casa",
                          "descripcion": "Artículos para el hogar"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("categoria-1"))
                .andExpect(jsonPath("$.nombre").value("Casa"))
                .andExpect(jsonPath("$.descripcion").value("Artículos para el hogar"));

        verify(categoriaService).modificarCategoria(
                eq("categoria-1"),
                any(ModificarCategoriaRequest.class));
    }

    @Test
    void clienteNoPuedeModificarCategoria() throws Exception {
        mockMvc.perform(put("/categorias/categoria-1")
                .with(user("cliente").roles("CLIENTE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void vendedorNoPuedeModificarCategoria() throws Exception {
        mockMvc.perform(put("/categorias/categoria-1")
                .with(user("vendedor").roles("VENDEDOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void usuarioNoAutenticadoNoPuedeModificarCategoria() throws Exception {
        mockMvc.perform(put("/categorias/categoria-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void rechazaDatosNoValidosAntesDeLlamarAlServicio() throws Exception {
        mockMvc.perform(put("/categorias/categoria-1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "   ",
                          "descripcion": ""
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").isArray())
                .andExpect(jsonPath("$.errores.descripcion").isArray());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void devuelve404SiLaCategoriaNoExiste() throws Exception {
        when(categoriaService.modificarCategoria(
                eq("categoria-inexistente"),
                any(ModificarCategoriaRequest.class)))
                .thenThrow(new CategoriaNoEncontradaException());

        mockMvc.perform(put("/categorias/categoria-inexistente")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("No se ha encontrado la categoría solicitada."));
    }

    @Test
    void devuelve409SiElNombreYaPerteneceAOtraCategoria() throws Exception {
        when(categoriaService.modificarCategoria(
                eq("categoria-1"),
                any(ModificarCategoriaRequest.class)))
                .thenThrow(new NombreCategoriaDuplicadoException());

        mockMvc.perform(put("/categorias/categoria-1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Ya existe una categoría con ese nombre."));
    }

    private String cuerpoValido() {
        return """
                {
                  "nombre": "Casa",
                  "descripcion": "Artículos para el hogar"
                }
                """;
    }
}
