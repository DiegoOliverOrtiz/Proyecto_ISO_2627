package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroVendedorRequest(
		@NotBlank String nombre,
		@NotBlank String apellidos,
		@NotBlank @Size(min = 9, max = 9, message = "El DNI/NIF debe tener 9 caracteres.") @Pattern(regexp = "^[0-9]{8}[A-Za-z]$", message = "El DNI/NIF debe tener 8 números y una letra.") String dniNif,
		@NotBlank @Email String email,
		@NotBlank String contrasena,
		@NotBlank String categoriaId,
		@NotBlank String nombreComercial,
		String telefono,
		String imagenPerfil) {

	public RegistroVendedorRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}