package es.uclm.esibuy.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class DireccionRequestTests {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private Set<String> camposConError(DireccionRequest request) {
		return VALIDATOR.validate(request).stream()
				.map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void direccionCompleta_noTieneErrores() {
		DireccionRequest request = new DireccionRequest(
				"Mi casa", "Calle Mayor", "1", "A", "2A", "13001", "Ciudad Real", "Ciudad Real");

		assertTrue(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void portalYPisoSonOpcionales() {
		DireccionRequest sinValores = new DireccionRequest(
				"Mi casa", "Calle Mayor", "1", null, null, "13001", "Ciudad Real", "Ciudad Real");
		DireccionRequest enBlanco = new DireccionRequest(
				"Mi casa", "Calle Mayor", "1", "", "   ", "13001", "Ciudad Real", "Ciudad Real");

		assertTrue(VALIDATOR.validate(sinValores).isEmpty());
		assertTrue(VALIDATOR.validate(enBlanco).isEmpty());
	}

	@Test
	void camposObligatoriosVaciosONulos_seRechazan() {
		DireccionRequest request = new DireccionRequest(" ", "", "  ", null, null, null, "   ", "");

		assertEquals(
				Set.of("nombre", "calle", "numero", "codigoPostal", "ciudad", "provincia"),
				camposConError(request));
	}

	@Test
	void camposDemasiadoLargos_seRechazan() {
		DireccionRequest request = new DireccionRequest(
				"a".repeat(51), "Calle Mayor", "a".repeat(11), "a".repeat(11), "a".repeat(11),
				"13001", "Ciudad Real", "Ciudad Real");

		assertEquals(Set.of("nombre", "numero", "portal", "piso"), camposConError(request));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1300", "130011", "1300A", "abcde", "13 01", "-1300" })
	void codigoPostalConFormatoIncorrecto_seRechaza(String codigoPostal) {
		DireccionRequest request = new DireccionRequest(
				"Mi casa", "Calle Mayor", "1", null, null, codigoPostal, "Ciudad Real", "Ciudad Real");

		assertEquals(Set.of("codigoPostal"), camposConError(request));
	}
}