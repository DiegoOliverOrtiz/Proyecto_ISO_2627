package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DireccionRequest(
		@NotBlank(message = "El nombre de la dirección es obligatorio.")
		@Size(max = 50, message = "El nombre de la dirección no puede superar los 50 caracteres.")
		String nombre,

		@NotBlank(message = "La calle es obligatoria.")
		@Size(max = 150, message = "La calle no puede superar los 150 caracteres.")
		String calle,

		@NotBlank(message = "El número es obligatorio.")
		@Size(max = 10, message = "El número no puede superar los 10 caracteres.")
		String numero,

		@Size(max = 10, message = "El portal no puede superar los 10 caracteres.")
		String portal,

		@Size(max = 10, message = "El piso no puede superar los 10 caracteres.")
		String piso,

		@NotBlank(message = "El código postal es obligatorio.")
		@Pattern(regexp = "\\d{5}", message = "El código postal debe tener 5 dígitos.")
		String codigoPostal,

		@NotBlank(message = "La ciudad es obligatoria.")
		@Size(max = 80, message = "La ciudad no puede superar los 80 caracteres.")
		String ciudad,

		@NotBlank(message = "La provincia es obligatoria.")
		@Size(max = 80, message = "La provincia no puede superar los 80 caracteres.")
		String provincia) {
}