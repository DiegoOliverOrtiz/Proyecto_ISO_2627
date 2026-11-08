package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import es.uclm.esibuy.backend.dto.RegistroClienteRequest;
import es.uclm.esibuy.backend.dto.RegistroPublicoResponse;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.dto.RegistroVendedorRequest;
import es.uclm.esibuy.backend.exceptions.CorreoYaRegistradoException;
import es.uclm.esibuy.backend.services.ClienteService;
import es.uclm.esibuy.backend.services.ProductoService;
import es.uclm.esibuy.backend.services.VendedorService;

class RegistrationValidationControllerTests {
	private final ClienteService clienteService = mock(ClienteService.class);
	private final VendedorService vendedorService = mock(VendedorService.class);
	private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(
				new ClienteController(clienteService, mock(ProductoService.class)),
				new VendedorController(vendedorService, mock(ProductoService.class)))
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
	void newAndDuplicateEmailsReturnTheSameGenericAcceptedResponse() throws Exception {
		when(clienteService.registrar(any(RegistroClienteRequest.class)))
				.thenReturn(new RegistroUsuarioResponse("customer-1", "cliente"))
				.thenThrow(new CorreoYaRegistradoException());

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

		MvcResult newEmail = mockMvc.perform(post("/cliente/register")
					.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RegistroPublicoResponse.confirmacion().mensaje()))
				.andReturn();

		MvcResult duplicateEmail = mockMvc.perform(post("/cliente/register")
					.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RegistroPublicoResponse.confirmacion().mensaje()))
				.andReturn();

		assertEquals(newEmail.getResponse().getStatus(), duplicateEmail.getResponse().getStatus());
		assertEquals(newEmail.getResponse().getContentAsString(), duplicateEmail.getResponse().getContentAsString());
		assertFalse(duplicateEmail.getResponse().getContentAsString().contains("ada@example.com"));
		assertFalse(duplicateEmail.getResponse().getContentAsString().contains("customer-1"));
	}

	@Test
	void newAndDuplicateSellerEmailsReturnTheSameGenericAcceptedResponse() throws Exception {
		when(vendedorService.registrar(any(RegistroVendedorRequest.class)))
				.thenReturn(new RegistroUsuarioResponse("seller-1", "vendedor"))
				.thenThrow(new CorreoYaRegistradoException());

		String request = """
				{
				  "nombre": "Ada",
				  "apellidos": "Lovelace",
				  "dniNif": "12345678Z",
				  "email": "ada@example.com",
				  "contrasena": "ClaveSegura9!",
				  "categoriaId": "categoria-1",
				  "nombreComercial": "Ada Shop"
				}
				""";

		MvcResult newEmail = mockMvc.perform(post("/vendedor/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RegistroPublicoResponse.confirmacion().mensaje()))
				.andReturn();

		MvcResult duplicateEmail = mockMvc.perform(post("/vendedor/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RegistroPublicoResponse.confirmacion().mensaje()))
				.andReturn();

		assertEquals(newEmail.getResponse().getStatus(), duplicateEmail.getResponse().getStatus());
		assertEquals(newEmail.getResponse().getContentAsString(), duplicateEmail.getResponse().getContentAsString());
		assertFalse(duplicateEmail.getResponse().getContentAsString().contains("ada@example.com"));
		assertFalse(duplicateEmail.getResponse().getContentAsString().contains("seller-1"));
	}
}
