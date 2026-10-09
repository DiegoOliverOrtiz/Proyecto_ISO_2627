package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.services.CategoriaService;

@WebMvcTest(controllers = CategoriaController.class)
@Import(SecurityConfiguration.class)
class CategoriaControllerHU11Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaService categoriaService;

    @Test
    void administradorPuedeConsultarElListadoDeCategorias() throws Exception {
        Categoria deportes = new Categoria(
                "Deportes",
                "Productos y equipamiento deportivo");
        deportes.setId("categoria-1");

        Categoria electronica = new Categoria(
                "Electrónica",
                "Productos electrónicos");
        electronica.setId("categoria-2");

        when(categoriaService.obtenerTodas())
                .thenReturn(List.of(deportes, electronica));

        mockMvc.perform(get("/categorias")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("categoria-1"))
                .andExpect(jsonPath("$[0].nombre").value("Deportes"))
                .andExpect(jsonPath("$[0].descripcion")
                        .value("Productos y equipamiento deportivo"))
                .andExpect(jsonPath("$[1].id").value("categoria-2"))
                .andExpect(jsonPath("$[1].nombre").value("Electrónica"));

        verify(categoriaService).obtenerTodas();
    }

    @Test
    void administradorRecibeListaVaciaSiNoHayCategorias() throws Exception {
        when(categoriaService.obtenerTodas())
                .thenReturn(List.of());

        mockMvc.perform(get("/categorias")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(categoriaService).obtenerTodas();
    }

    @Test
    void administradorPuedeConsultarElDetalleDeUnaCategoria() throws Exception {
        Categoria categoria = new Categoria(
                "Deportes",
                "Productos y equipamiento deportivo");
        categoria.setId("categoria-1");

        when(categoriaService.obtenerPorId("categoria-1"))
                .thenReturn(Optional.of(categoria));

        mockMvc.perform(get("/categorias/categoria-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("categoria-1"))
                .andExpect(jsonPath("$.nombre").value("Deportes"))
                .andExpect(jsonPath("$.descripcion")
                        .value("Productos y equipamiento deportivo"));

        verify(categoriaService).obtenerPorId("categoria-1");
    }

    @Test
    void devuelve404SiLaCategoriaNoExiste() throws Exception {
        when(categoriaService.obtenerPorId("categoria-inexistente"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/categorias/categoria-inexistente")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());

        verify(categoriaService).obtenerPorId("categoria-inexistente");
    }

    @Test
    void clienteNoPuedeConsultarCategorias() throws Exception {
        mockMvc.perform(get("/categorias")
                        .with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void vendedorPuedeConsultarCategorias() throws Exception {
        // HU17: el vendedor necesita las categorías para asociarlas a sus productos.
        Categoria deportes = new Categoria(
                "Deportes",
                "Productos y equipamiento deportivo");
        deportes.setId("categoria-1");

        when(categoriaService.obtenerTodas())
                .thenReturn(List.of(deportes));

        mockMvc.perform(get("/categorias")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Deportes"));

        verify(categoriaService).obtenerTodas();
    }

    @Test
    void usuarioNoAutenticadoNoPuedeConsultarCategorias() throws Exception {
        mockMvc.perform(get("/categorias"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void categoriasDeRegistroSiguenSiendoPublicas() throws Exception {
        Categoria categoria = new Categoria(
                "Deportes",
                "Productos y equipamiento deportivo");
        categoria.setId("categoria-1");

        when(categoriaService.obtenerTodas())
                .thenReturn(List.of(categoria));

        mockMvc.perform(get("/categorias/registro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("categoria-1"))
                .andExpect(jsonPath("$[0].nombre").value("Deportes"))
                .andExpect(jsonPath("$[0].descripcion").doesNotExist());

        verify(categoriaService).obtenerTodas();
    }
}