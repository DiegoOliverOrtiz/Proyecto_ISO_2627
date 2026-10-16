package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.hamcrest.Matchers;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.services.AdministradorService;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;

@WebMvcTest(controllers = { AuthController.class, AdministradorController.class })
@Import({ SecurityConfiguration.class, SesionService.class })
class AdministradorEliminacionSeguridadTests {

	private static final String EMAIL = "admin.eliminado@esibuy.es";
	private static final String PASSWORD = "Clave-Segura-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SesionService sesionService;

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

	@Test
	void sesionDelAdministradorEliminado_dejaDeSerValidaTrasCerrarSusSesiones() throws Exception {
		Administrador administrador = new Administrador();
		administrador.setEmail(EMAIL);
		when(authService.autenticar(EMAIL, PASSWORD)).thenReturn(administrador);

		MockHttpSession sesion = (MockHttpSession) mockMvc.perform(post("/usuarios/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(get("/usuarios/sesion-actual").session(sesion))
				.andExpect(status().isOk());

		sesionService.cerrarSesiones(EMAIL);

		mockMvc.perform(get("/usuarios/sesion-actual").session(sesion))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void preflightDeDelete_desdeElOrigenDelFrontend_estaPermitido() throws Exception {
		mockMvc.perform(options("/administrador/{id}", "id-1")
						.header("Origin", "http://localhost:4200")
						.header("Access-Control-Request-Method", "DELETE")
						.header("Access-Control-Request-Headers", "x-xsrf-token"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
				.andExpect(header().string("Access-Control-Allow-Methods",
						Matchers.containsString("DELETE")));
	}
}