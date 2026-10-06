package es.uclm.esibuy.backend.dto;

import java.time.LocalDate;

public record UsuarioAdministracionResponse(
		String id,
		String nombre,
		String apellidos,
		String email,
		String telefono,
		String estado,
		String rol,
		String imagenPerfil,
		String sede,
		LocalDate fechaIncorporacion,
		Boolean esPremium,
		String nombreComercial) {
}
