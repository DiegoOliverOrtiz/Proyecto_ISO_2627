package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.exceptions.CategoriaConProductosException;
import es.uclm.esibuy.backend.exceptions.CategoriaNoEncontradaException;
import es.uclm.esibuy.backend.services.CategoriaService;

@WebMvcTest(controllers = CategoriaController.class)
@Import(SecurityConfiguration.class)
class CategoriaControllerHU14Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaService categoriaService;

    @Test
    void administradorPuedeEliminarCategoria() throws Exception {
        mockMvc.perform(delete("/categorias/categoria-1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(categoriaService).eliminarCategoria("categoria-1");
    }

    @Test
    void clienteNoPuedeEliminarCategoria() throws Exception {
        mockMvc.perform(delete("/categorias/categoria-1")
                .with(user("cliente").roles("CLIENTE"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void vendedorNoPuedeEliminarCategoria() throws Exception {
        mockMvc.perform(delete("/categorias/categoria-1")
                .with(user("vendedor").roles("VENDEDOR"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void usuarioNoAutenticadoNoPuedeEliminarCategoria() throws Exception {
        mockMvc.perform(delete("/categorias/categoria-1")
                .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void devuelve404SiLaCategoriaNoExiste() throws Exception {
        doThrow(new CategoriaNoEncontradaException())
                .when(categoriaService)
                .eliminarCategoria("categoria-inexistente");

        mockMvc.perform(delete("/categorias/categoria-inexistente")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("No se ha encontrado la categoría solicitada."));
    }

    @Test
    void devuelve409SiLaCategoriaTieneProductosAsociados() throws Exception {
        doThrow(new CategoriaConProductosException())
                .when(categoriaService)
                .eliminarCategoria("categoria-1");

        mockMvc.perform(delete("/categorias/categoria-1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("No se puede eliminar una categoría con productos asociados."));
    }
}