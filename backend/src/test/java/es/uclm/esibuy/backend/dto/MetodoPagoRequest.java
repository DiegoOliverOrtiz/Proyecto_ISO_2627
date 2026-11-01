package es.uclm.esibuy.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class MetodoPagoRequestTests {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private Set<String> camposConError(MetodoPagoRequest request) {
		return VALIDATOR.validate(request).stream()
				.map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void toString_noExponeLosDatosDePago() {
		MetodoPagoRequest request = new MetodoPagoRequest(
				"TARJETA", "Visa personal", "4242424242424242", "Ana Perez", "12/40", "987",
				"juan@gmail.com", "612345678");

		String texto = request.toString();

		assertTrue(texto.contains("Visa personal"));
		assertFalse(texto.contains("4242424242424242"));
		assertFalse(texto.contains("Ana Perez"));
		assertFalse(texto.contains("987"));
		assertFalse(texto.contains("juan@gmail.com"));
		assertFalse(texto.contains("612345678"));
	}

	@Test
	void tipoYNombreSonObligatorios() {
		MetodoPagoRequest request = new MetodoPagoRequest(" ", "", null, null, null, null, null, null);

		assertEquals(Set.of("tipo", "nombre"), camposConError(request));
	}

	@Test
	void camposDemasiadoLargos_seRechazan() {
		MetodoPagoRequest request = new MetodoPagoRequest(
				"a".repeat(31), "a".repeat(51), "1".repeat(31), "a".repeat(61), "1".repeat(8),
				"1".repeat(5), "a".repeat(255), "1".repeat(21));

		assertEquals(
				Set.of("tipo", "nombre", "numeroTarjeta", "titular", "caducidad", "cvv",
						"correoPaypal", "telefonoBizum"),
				camposConError(request));
	}

	@Test
	void peticionCorrecta_noTieneErrores() {
		MetodoPagoRequest request = new MetodoPagoRequest(
				"PAYPAL", "Mi PayPal", null, null, null, null, "juan@gmail.com", null);

		assertTrue(VALIDATOR.validate(request).isEmpty());
	}
}