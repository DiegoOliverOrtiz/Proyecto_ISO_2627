package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.services.AdministradorService;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;

@WebMvcTest(controllers = { AuthController.class, AdministradorController.class })
@Import({ SecurityConfiguration.class, SesionService.class })
class AdministradorEliminacionSeguridadTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdministradorService administradorService;
    	
    @MockitoBean
	private AuthService authService;
    
	@Test
	@WithMockUser(username = "admin@esibuy.es", roles = "ADMIN")
	void eliminar_comoAdministradorConCsrf_devuelve204YDelegaEnElServicio() throws Exception {
		mockMvc.perform(delete("/administrador/{id}", "id-1").with(csrf()))
				.andExpect(status().isNoContent());

		verify(administradorService).eliminarAdministrador("id-1", "admin@esibuy.es");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void eliminar_sinTokenCsrf_devuelve403() throws Exception {
		mockMvc.perform(delete("/administrador/{id}", "id-1"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "CLIENTE")
	void eliminar_comoCliente_devuelve403() throws Exception {
		mockMvc.perform(delete("/administrador/{id}", "id-1").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "VENDEDOR")
	void eliminar_comoVendedor_devuelve403() throws Exception {
		mockMvc.perform(delete("/administrador/{id}", "id-1").with(csrf()))
				.andExpect(status().isForbidden());
	}

    @Test
    void eliminar_sinAutenticar_devuelve403() throws Exception {
        mockMvc.perform(delete("/administrador/{id}", "id-1").with(csrf()))
                .andExpect(status().isForbidden());
    }
}