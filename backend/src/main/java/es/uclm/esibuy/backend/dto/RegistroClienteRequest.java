package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import es.uclm.esibuy.backend.validation.Adult;

public record RegistroClienteRequest(
		@NotBlank String nombre,
		@NotBlank String apellidos,
		@NotNull @Adult LocalDate fechaNacimiento,
		@NotBlank @Size(min = 9, max = 9, message = "El DNI/NIF debe tener 9 caracteres.") @Pattern(regexp = "^[0-9]{8}[A-Za-z]$", message = "El DNI/NIF debe tener 8 números y una letra.") String dniNif,
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