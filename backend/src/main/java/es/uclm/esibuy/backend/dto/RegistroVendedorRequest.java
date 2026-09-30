package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroVendedorRequest(
		@NotBlank String nombre,
		@NotBlank String apellidos,
		@NotBlank @Size(max = 9) String dniNif,
		@NotBlank @Email String email,
		@NotBlank String contrasena,
		@NotBlank String categoria,
		@NotBlank String nombreComercial,
		String telefono,
		String imagenPerfil) {

	public RegistroVendedorRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}