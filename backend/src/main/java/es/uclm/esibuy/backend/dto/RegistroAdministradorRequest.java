package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistroAdministradorRequest(
		@NotBlank String nombre,
		@NotBlank String apellidos,
		@NotBlank @Email String email,
		@NotBlank String contrasena,
		String sede,
		LocalDate fechaIncorporacion,
		String imagenPerfil) {

	public RegistroAdministradorRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}