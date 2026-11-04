package es.uclm.esibuy.backend.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.backend.config.SecurityConfiguration;
import es.uclm.esibuy.backend.dto.MetodoPagoRequest;
import es.uclm.esibuy.backend.dto.MetodoPagoResponse;
import es.uclm.esibuy.backend.dto.TipoMetodoPagoResponse;
import es.uclm.esibuy.backend.exceptions.MetodoPagoDuplicadoException;
import es.uclm.esibuy.backend.exceptions.MetodoPagoInvalidoException;
import es.uclm.esibuy.backend.services.PagoSimuladoService;
import es.uclm.esibuy.backend.exceptions.MetodoPagoNoEncontradoException;

@WebMvcTest(controllers = MetodoPagoController.class)
@Import(SecurityConfiguration.class)
class MetodoPagoControllerTests {

	private static final String EMAIL = "cliente@example.com";
	private static final String URL = "/cliente/metodos-pago";
	private static final String CUERPO_TARJETA = """
			{"tipo":"TARJETA","nombre":"Visa personal","numeroTarjeta":"4242 4242 4242 4242","titular":"Ana Perez","caducidad":"12/40","cvv":"123"}
			""";
	private static final String CUERPO_SIN_TIPO = """
			{"nombre":"Visa personal"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PagoSimuladoService pagoSimuladoService;

	private MetodoPagoResponse respuesta() {
		return new MetodoPagoResponse("mp-1", "TARJETA", "Visa personal", "**** **** **** 4242", "Ana Perez", "12/40");
	}

	@Test
	void consultaLosTiposDisponibles() throws Exception {
		when(pagoSimuladoService.consultarTiposDisponibles()).thenReturn(List.of(
				new TipoMetodoPagoResponse("BIZUM", "Bizum"),
				new TipoMetodoPagoResponse("TARJETA", "Tarjeta")));

		mockMvc.perform(get(URL + "/tipos").with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].codigo").value("BIZUM"))
				.andExpect(jsonPath("$[1].nombre").value("Tarjeta"));
	}

	@Test
	void registraUnMetodoDePagoYDevuelveSoloDatosEnmascarados() throws Exception {
		when(pagoSimuladoService.registrarMetodoPago(eq(EMAIL), any(MetodoPagoRequest.class)))
				.thenReturn(respuesta());

		mockMvc.perform(post(URL)
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_TARJETA))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("mp-1"))
				.andExpect(jsonPath("$.datosEnmascarados").value("**** **** **** 4242"))
				.andExpect(jsonPath("$.token").doesNotExist())
				.andExpect(content().string(not(containsString("4242 4242 4242 4242"))))
				.andExpect(content().string(not(containsString("4242424242424242"))));

		verify(pagoSimuladoService).registrarMetodoPago(eq(EMAIL), any(MetodoPagoRequest.class));
	}

	@Test
	void rechazaUnaPeticionSinTipoConUn400SinLlamarAlServicio() throws Exception {
		mockMvc.perform(post(URL)
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_SIN_TIPO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Revisa los campos del formulario."));

		verifyNoInteractions(pagoSimuladoService);
	}

	@Test
	void datosDePagoInvalidos_devuelve400ConElMotivo() throws Exception {
		when(pagoSimuladoService.registrarMetodoPago(eq(EMAIL), any(MetodoPagoRequest.class)))
				.thenThrow(new MetodoPagoInvalidoException(
						Map.of("numeroTarjeta", List.of("El número de tarjeta no es válido."))));

		mockMvc.perform(post(URL)
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_TARJETA))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Revisa los datos del método de pago."));
	}

	@Test
	void nombreRepetido_devuelve409() throws Exception {
		when(pagoSimuladoService.registrarMetodoPago(eq(EMAIL), any(MetodoPagoRequest.class)))
				.thenThrow(new MetodoPagoDuplicadoException());

		mockMvc.perform(post(URL)
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_TARJETA))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya tienes un método de pago con ese nombre."));
	}

	@Test
	void sinTokenCsrf_elAltaDevuelve403() throws Exception {
		mockMvc.perform(post(URL)
						.with(user(EMAIL).roles("CLIENTE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CUERPO_TARJETA))
				.andExpect(status().isForbidden());

		verifyNoInteractions(pagoSimuladoService);
	}

	@Test
	void vendedoresAdministradoresYAnonimos_noPuedenUsarLosMetodosDePago() throws Exception {
		for (String rol : List.of("VENDEDOR", "ADMIN")) {
			mockMvc.perform(get(URL + "/tipos").with(user("otro@example.com").roles(rol)))
					.andExpect(status().isForbidden());
			mockMvc.perform(post(URL)
							.with(user("otro@example.com").roles(rol))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(CUERPO_TARJETA))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(get(URL + "/tipos")).andExpect(status().isForbidden());

		verifyNoInteractions(pagoSimuladoService);
	}

	@Test
	void consultaLosMetodosDePagoDelClienteSinExponerElToken() throws Exception {
		// CAMBIO: consultarMetodos en lugar de consultarMetodosPago + anyString()
		when(pagoSimuladoService.consultarMetodos(anyString())).thenReturn(List.of(respuesta()));

		mockMvc.perform(get(URL).with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Visa personal"))
				.andExpect(jsonPath("$[0].tipo").value("TARJETA"))
				.andExpect(jsonPath("$[0].datosEnmascarados").value("**** **** **** 4242"))
				.andExpect(jsonPath("$[0].token").doesNotExist());

		// CAMBIO: verificar consultarMetodos
		verify(pagoSimuladoService).consultarMetodos(eq(EMAIL));
	}

	@Test
	void sinMetodosDePago_devuelveUnaListaVaciaConEstado200() throws Exception {
		// CAMBIO: consultarMetodos en lugar de consultarMetodosPago + anyString()
		when(pagoSimuladoService.consultarMetodos(anyString())).thenReturn(List.of());

		mockMvc.perform(get(URL).with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void vendedoresAdministradoresYAnonimos_noPuedenConsultarMetodosDePago() throws Exception {
		mockMvc.perform(get(URL).with(user("otro@example.com").roles("VENDEDOR")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(URL).with(user("otro@example.com").roles("ADMIN")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(URL)).andExpect(status().isForbidden());

		verifyNoInteractions(pagoSimuladoService);
	}

		@Test
	void eliminaUnMetodoDePagoYDevuelve204() throws Exception {
		mockMvc.perform(delete(URL + "/{id}", "mp-1")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf()))
				.andExpect(status().isNoContent());

		verify(pagoSimuladoService).eliminarMetodoPago(EMAIL, "mp-1");
	}

	@Test
	void metodoInexistenteOAjeno_devuelve404ConElMotivo() throws Exception {
		doThrow(new MetodoPagoNoEncontradoException())
				.when(pagoSimuladoService).eliminarMetodoPago(EMAIL, "mp-ajeno");

		mockMvc.perform(delete(URL + "/{id}", "mp-ajeno")
						.with(user(EMAIL).roles("CLIENTE"))
						.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No se encontró el método de pago indicado."));
	}

	@Test
	void sinTokenCsrf_laBajaDevuelve403() throws Exception {
		mockMvc.perform(delete(URL + "/{id}", "mp-1").with(user(EMAIL).roles("CLIENTE")))
				.andExpect(status().isForbidden());

		verifyNoInteractions(pagoSimuladoService);
	}

	@Test
	void vendedoresAdministradoresYAnonimos_noPuedenEliminarMetodosDePago() throws Exception {
		for (String rol : List.of("VENDEDOR", "ADMIN")) {
			mockMvc.perform(delete(URL + "/{id}", "mp-1")
							.with(user("otro@example.com").roles(rol))
							.with(csrf()))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(delete(URL + "/{id}", "mp-1").with(csrf()))
				.andExpect(status().isForbidden());

		verifyNoInteractions(pagoSimuladoService);
	}
}