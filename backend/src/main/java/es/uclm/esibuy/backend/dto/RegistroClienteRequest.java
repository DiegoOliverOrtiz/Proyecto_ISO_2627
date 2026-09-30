package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroClienteRequest(
		@NotBlank String nombre,
		@NotBlank String apellidos,
		@NotNull LocalDate fechaNacimiento,
		@NotBlank @Size(max = 9) String dniNif,
		@NotBlank @Email String email,
		@NotBlank String contrasena,
		String telefono,
		String imagenPerfil,
		boolean esPremium) {

	public RegistroClienteRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}