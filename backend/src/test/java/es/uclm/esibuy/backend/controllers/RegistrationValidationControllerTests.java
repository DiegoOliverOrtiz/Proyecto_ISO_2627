package es.uclm.esibuy.backend.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import es.uclm.esibuy.backend.services.ClienteService;

class RegistrationValidationControllerTests {
	private final ClienteService clienteService = mock(ClienteService.class);
	private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ClienteController(clienteService))
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
}
