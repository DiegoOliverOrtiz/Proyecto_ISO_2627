package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetodoPagoRequest(
		@NotBlank(message = "El tipo de método de pago es obligatorio.")
		@Size(max = 30, message = "El tipo de método de pago no es válido.")
		String tipo,

		@NotBlank(message = "El nombre del método de pago es obligatorio.")
		@Size(max = 50, message = "El nombre del método de pago no puede superar los 50 caracteres.")
		String nombre,

		@Size(max = 30, message = "El número de tarjeta no es válido.")
		String numeroTarjeta,

		@Size(max = 60, message = "El titular no puede superar los 60 caracteres.")
		String titular,

		@Size(max = 7, message = "La caducidad debe tener el formato MM/AA.")
		String caducidad,

		@Size(max = 4, message = "El código de seguridad debe tener 3 o 4 dígitos.")
		String cvv,

		@Size(max = 254, message = "El correo de PayPal no es válido.")
		String correoPaypal,

		@Size(max = 20, message = "El teléfono de Bizum no es válido.")
		String telefonoBizum) {

	@Override
	public String toString() {
		return "MetodoPagoRequest[tipo=" + tipo + ", nombre=" + nombre + ", datosDePago=<ocultos>]";
	}
}