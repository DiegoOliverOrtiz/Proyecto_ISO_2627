package es.uclm.esibuy.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.DireccionRequest;
import es.uclm.esibuy.backend.dto.DireccionResponse;
import es.uclm.esibuy.backend.exceptions.DireccionDuplicadaException;
import es.uclm.esibuy.backend.exceptions.DireccionNoEncontradaException;
import es.uclm.esibuy.backend.services.ClienteService;
import es.uclm.esibuy.backend.services.PerfilService;
import es.uclm.esibuy.backend.services.ProductoService;

@WebMvcTest(controllers = ClienteController.class)
@Import(SecurityConfiguration.class)
class ClienteControllerDireccionesTests {

	private static final String EMAIL = "cliente@example.com";
	private static final String CUERPO_VALIDO = """
			{"nombre":"Mi casa","calle":"Calle Mayor","numero":"1","portal":"A","piso":"2A",
			 "codigoPostal":"13001","ciudad":"Ciudad Real","provincia":"Ciudad Real"}
			""";
	private static final String CUERPO_SIN_PORTAL_NI_PISO = """
			{"nombre":"Mi casa","calle":"Calle Mayor","numero":"1",
			 "codigoPostal":"13001","ciudad":"Ciudad Real","provincia":"Ciudad Real"}
			""";
	private static final String CUERPO_CODIGO_POSTAL_INVALIDO = """
			{"nombre":"Mi casa","calle":"Calle Mayor","numero":"1",
			 "codigoPostal":"1","ciudad":"Ciudad Real","provincia":"Ciudad Real"}
			""";
	private static final String CUERPO_SIN_NUMERO = """
			{"nombre":"Mi casa","calle":"Calle Mayor",
			 "codigoPostal":"13001","ciudad":"Ciudad Real","provincia":"Ciudad Real"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClienteService clienteService;

	@MockitoBean
	private ProductoService productoService;

	@MockitoBean
	private PerfilService perfilService;

	private DireccionResponse respuesta() {
		return new DireccionResponse(
				"dir-1", "Mi casa", "Calle Mayor", "1", "A", "2A", "13001", "Ciudad Real", "Ciudad Real");
	}

	@Test
	void consultaLasDireccionesDelClienteAutenticado() throws Exception {
		when(clienteService.consultarDirecciones(EMAIL)).thenReturn(List.of(respuesta()));

		mockMvc.perform(get("/cliente/direcciones").with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("dir-1"))
				.andExpect(jsonPath("$[0].nombre").value("Mi casa"))
				.andExpect(jsonPath("$[0].numero").value("1"))
				.andExpect(jsonPath("$[0].portal").value("A"))
				.andExpect(jsonPath("$[0].piso").value("2A"));

		verify(clienteService).consultarDirecciones(EMAIL);
	}

	@Test
	void agregaUnaDireccionYDevuelve201() throws Exception {
		when(clienteService.agregarDireccion(eq(EMAIL), any(DireccionRequest.class))).thenReturn(respuesta());

		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_VALIDO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("dir-1"));
	}

	@Test
	void agregaUnaDireccionSinPortalNiPiso() throws Exception {
		when(clienteService.agregarDireccion(eq(EMAIL), any(DireccionRequest.class))).thenReturn(respuesta());

		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_SIN_PORTAL_NI_PISO))
				.andExpect(status().isCreated());
	}

	@Test
	void modificaUnaDireccion() throws Exception {
		when(clienteService.modificarDireccion(eq(EMAIL), eq("dir-1"), any(DireccionRequest.class)))
				.thenReturn(respuesta());

		mockMvc.perform(put("/cliente/direcciones/{id}", "dir-1")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_VALIDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Mi casa"));
	}

	@Test
	void eliminaUnaDireccionYDevuelve204() throws Exception {
		mockMvc.perform(delete("/cliente/direcciones/{id}", "dir-1")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf()))
				.andExpect(status().isNoContent());

		verify(clienteService).eliminarDireccion(EMAIL, "dir-1");
	}

	@Test
	void rechazaDatosInvalidosConUn400SinLlamarAlServicio() throws Exception {
		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_CODIGO_POSTAL_INVALIDO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Revisa los campos del formulario."));

		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_SIN_NUMERO))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/cliente/direcciones/{id}", "dir-1")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_CODIGO_POSTAL_INVALIDO))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(clienteService);
	}

	@Test
	void direccionInexistenteOAjena_devuelve404() throws Exception {
		doThrow(new DireccionNoEncontradaException())
				.when(clienteService).eliminarDireccion(EMAIL, "dir-ajena");

		mockMvc.perform(delete("/cliente/direcciones/{id}", "dir-ajena")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No se encontró la dirección indicada."));
	}

	@Test
	void nombreDeDireccionRepetido_devuelve409() throws Exception {
		when(clienteService.agregarDireccion(eq(EMAIL), any(DireccionRequest.class)))
				.thenThrow(new DireccionDuplicadaException());

		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya tienes una dirección con ese nombre."));
	}

	@Test
	void sinTokenCsrf_lasMutacionesDevuelven403() throws Exception {
		mockMvc.perform(post("/cliente/direcciones")
						.with(user(EMAIL).roles("CLIENTE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_VALIDO))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/cliente/direcciones/{id}", "dir-1")
						.with(user(EMAIL).roles("CLIENTE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_VALIDO))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/cliente/direcciones/{id}", "dir-1")
						.with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isForbidden());

		verifyNoInteractions(clienteService);
	}

	@Test
	void vendedoresAdministradoresYAnonimos_noPuedenGestionarDirecciones() throws Exception {
		for (String rol : List.of("VENDEDOR", "ADMIN")) {
			mockMvc.perform(get("/cliente/direcciones").with(user("otro@example.com").roles(rol)))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/cliente/direcciones")
							.with(user("otro@example.com").roles(rol))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(CUERPO_VALIDO))
					.andExpect(status().isForbidden());
			mockMvc.perform(delete("/cliente/direcciones/{id}", "dir-1")
							.with(user("otro@example.com").roles(rol))
							.with(csrf()))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/cliente/direcciones")).andExpect(status().isForbidden());

		verifyNoInteractions(clienteService);
	}
}