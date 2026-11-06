package es.uclm.esibuy.backend.controllers;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.services.ClienteService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.SoporteRegistro;

class RegistrationValidationControllerTests {
	private final ClienteService clienteService = mock(ClienteService.class);
	private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ClienteController(clienteService, mock(ProductoService.class)))
				.setControllerAdvice(new RegistrationExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void returnsAllValidationErrorsForAnUnderageCustomerRequest() throws Exception {
		String underageDate = LocalDate.now().minusYears(17).toString();
		String request = """
				{
				  "nombre": "",
				  "apellidos": "Lovelace",
				  "fechaNacimiento": "%s",
				  "dniNif": "12345678A",
				  "email": "invalid-email",
				  "contrasena": "ClaveSegura9!",
				  "esPremium": false
				}
				""".formatted(underageDate);

		mockMvc.perform(post("/cliente/register")
					.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Revisa los campos del formulario."))
				.andExpect(jsonPath("$.errores.nombre[0]").exists())
				.andExpect(jsonPath("$.errores.fechaNacimiento[0]")
						.value("El cliente debe tener al menos 18 años."))
				.andExpect(jsonPath("$.errores.email[0]").exists());

		verifyNoInteractions(clienteService);
	}

	@Test
	void duplicateEmailResponseDoesNotRevealThatTheEmailExists() throws Exception {
		when(clienteService.registrar(any(RegistroClienteRequest.class)))
				.thenThrow(new InvalidRegistrationException(SoporteRegistro.MENSAJE_DATOS_NO_VALIDOS));

		String request = """
				{
				  "nombre": "Ada",
				  "apellidos": "Lovelace",
				  "fechaNacimiento": "1990-12-10",
				  "dniNif": "12345678Z",
				  "email": "ada@example.com",
				  "contrasena": "ClaveSegura9!",
				  "esPremium": false
				}
				""";

		mockMvc.perform(post("/cliente/register")
					.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Los datos introducidos no son correctos."))
				.andExpect(jsonPath("$.errores.email").doesNotExist())
				.andExpect(content().string(not(containsString("existe"))))
				.andExpect(content().string(not(containsString("correo"))));
	}
}
