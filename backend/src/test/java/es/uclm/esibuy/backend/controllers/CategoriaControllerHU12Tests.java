package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import es.uclm.esibuy.backend.model.Categoria;
import es.uclm.esibuy.backend.services.CategoriaService;

@WebMvcTest(controllers = CategoriaController.class)
@Import(SecurityConfiguration.class)
class CategoriaControllerHU12Tests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CategoriaService categoriaService;

  @Test
  void administradorPuedeCrearCategoria() throws Exception {
    Categoria guardada = new Categoria("Hogar", "Productos para el hogar");
    guardada.setId("categoria-1");

    when(categoriaService.crearCategoria(any(Categoria.class)))
        .thenReturn(guardada);

    mockMvc.perform(post("/categorias")
        .with(user("admin").roles("ADMIN"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "nombre": "Hogar",
              "descripcion": "Productos para el hogar"
            }
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("categoria-1"))
        .andExpect(jsonPath("$.nombre").value("Hogar"))
        .andExpect(jsonPath("$.descripcion")
            .value("Productos para el hogar"));
  }

  @Test
  void clienteNoPuedeCrearCategoria() throws Exception {
    mockMvc.perform(post("/categorias")
        .with(user("cliente").roles("CLIENTE"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "nombre": "Hogar",
              "descripcion": "Productos para el hogar"
            }
            """))
        .andExpect(status().isForbidden());
  }

  @Test
  void vendedorNoPuedeCrearCategoria() throws Exception {
    mockMvc.perform(post("/categorias")
        .with(user("vendedor").roles("VENDEDOR"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "nombre": "Hogar",
              "descripcion": "Productos para el hogar"
            }
            """))
        .andExpect(status().isForbidden());
  }

}