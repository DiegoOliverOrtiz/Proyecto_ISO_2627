package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import es.uclm.esibuy.backend.dto.MetodoPagoRequest;

class ValidadorMetodoPagoTests {

	private final ValidadorMetodoPago validador = new ValidadorMetodoPago();

	private String caducidadFutura() {
		YearMonth futura = YearMonth.now().plusYears(2);
		return String.format(Locale.ROOT, "%02d/%02d", futura.getMonthValue(), futura.getYear() % 100);
	}

	private MetodoPagoRequest tarjeta(String numero, String titular, String caducidad, String cvv) {
		return new MetodoPagoRequest("TARJETA", "Mi tarjeta", numero, titular, caducidad, cvv, null, null);
	}

	private MetodoPagoRequest tarjetaValidaCon(String numero) {
		return tarjeta(numero, "Ana Pérez", caducidadFutura(), "123");
	}

	private MetodoPagoRequest paypal(String correo) {
		return new MetodoPagoRequest("PAYPAL", "Mi PayPal", null, null, null, null, correo, null);
	}

	private MetodoPagoRequest bizum(String telefono) {
		return new MetodoPagoRequest("BIZUM", "Mi Bizum", null, null, null, null, null, telefono);
	}

	private Set<String> camposConError(String tipo, MetodoPagoRequest request) {
		return validador.validar(tipo, request).errores().keySet();
	}

	@Test
	void tarjetaValida_normalizaLosDatosYNoTieneErrores() {
		ValidadorMetodoPago.Resultado resultado = validador.validar(
				"TARJETA", tarjeta("4242 4242 4242 4242", " Ana Pérez ", caducidadFutura(), "123"));

		assertTrue(resultado.esValido());
		assertEquals("4242424242424242", resultado.datoPrincipal());
		assertEquals("Ana Pérez", resultado.titular());
		assertEquals(caducidadFutura(), resultado.caducidad());
	}

	@Test
	void tarjetaConCaducidadDeCuatroDigitosDelAnio_seNormalizaADos() {
		String caducidad = "12/" + (Year.now().getValue() + 3);

		ValidadorMetodoPago.Resultado resultado = validador.validar(
				"TARJETA", tarjeta("4242424242424242", "Ana Pérez", caducidad, "1234"));

		assertTrue(resultado.esValido());
		assertEquals("12/" + String.valueOf(Year.now().getValue() + 3).substring(2), resultado.caducidad());
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234 5678 9012 3456", "4242 4242 4242 424A", "4242",
			"4242-4242-4242-4241", "42424242424242424242" })
	void numeroDeTarjetaInvalido_seRechaza(String numero) {
		assertEquals(Set.of("numeroTarjeta"), camposConError("TARJETA", tarjetaValidaCon(numero)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "13/30", "1/30", "2030", "00/30", "ab/cd", "01/20" })
	void caducidadInvalidaOVencida_seRechaza(String caducidad) {
		MetodoPagoRequest request = tarjeta("4242424242424242", "Ana Pérez", caducidad, "123");

		assertEquals(Set.of("caducidad"), camposConError("TARJETA", request));
	}

	@ParameterizedTest
	@ValueSource(strings = { "12", "12345", "abc", "1 2 3" })
	void cvvInvalido_seRechaza(String cvv) {
		MetodoPagoRequest request = tarjeta("4242424242424242", "Ana Pérez", caducidadFutura(), cvv);

		assertEquals(Set.of("cvv"), camposConError("TARJETA", request));
	}

	@ParameterizedTest
	@ValueSource(strings = { "A", "Juan<script>", "1234" })
	void titularInvalido_seRechaza(String titular) {
		MetodoPagoRequest request = tarjeta("4242424242424242", titular, caducidadFutura(), "123");

		assertEquals(Set.of("titular"), camposConError("TARJETA", request));
	}

	@Test
	void tarjetaSinDatos_rechazaTodosLosCamposObligatorios() {
		assertEquals(
				Set.of("numeroTarjeta", "titular", "caducidad", "cvv"),
				camposConError("TARJETA", tarjeta(null, null, null, null)));
	}

	@Test
	void paypalValido_normalizaElCorreo() {
		ValidadorMetodoPago.Resultado resultado = validador.validar("PAYPAL", paypal("  Juan@Gmail.COM "));

		assertTrue(resultado.esValido());
		assertEquals("juan@gmail.com", resultado.datoPrincipal());
	}

	@ParameterizedTest
	@ValueSource(strings = { "sinarroba", "a@b", "a@@b.com", "a b@c.com", "@x.com", "a@.com", "a@com." })
	void paypalInvalido_seRechaza(String correo) {
		assertEquals(Set.of("correoPaypal"), camposConError("PAYPAL", paypal(correo)));
	}

	@Test
	void paypalConCorreoDemasiadoLargo_seRechaza() {
		String correo = "a".repeat(250) + "@x.com";

		assertEquals(Set.of("correoPaypal"), camposConError("PAYPAL", paypal(correo)));
	}

	@Test
	void paypalSinCorreo_esObligatorio() {
		assertEquals(Set.of("correoPaypal"), camposConError("PAYPAL", paypal(null)));
	}

	@ParameterizedTest
	@CsvSource({ "612345678,612345678", "612 345 678,612345678", "+34 612 345 678,612345678",
			"712345678,712345678" })
	void bizumValido_normalizaElTelefono(String entrada, String esperado) {
		ValidadorMetodoPago.Resultado resultado = validador.validar("BIZUM", bizum(entrada));

		assertTrue(resultado.esValido());
		assertEquals(esperado, resultado.datoPrincipal());
	}

	@ParameterizedTest
	@ValueSource(strings = { "512345678", "61234567", "6123456789", "abc", "+33612345678" })
	void bizumInvalido_seRechaza(String telefono) {
		assertEquals(Set.of("telefonoBizum"), camposConError("BIZUM", bizum(telefono)));
	}

	@Test
	void bizumSinTelefono_esObligatorio() {
		assertEquals(Set.of("telefonoBizum"), camposConError("BIZUM", bizum(null)));
	}

	@Test
	void tipoNoSoportado_seRechaza() {
		assertEquals(Set.of("tipo"), camposConError("TRANSFERENCIA", tarjetaValidaCon("4242424242424242")));
	}
}