package es.uclm.esibuy.backend.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Service
public class SoporteRegistro {
	// Mensaje genérico: el registro no debe revelar si un correo ya está en uso.
	public static final String MENSAJE_DATOS_NO_VALIDOS = "Los datos introducidos no son correctos.";

	private final UsuarioDAO usuarioDAO;
	private final PasswordEncoder codificadorContrasena;
	private final PasswordPolicy politicaContrasenas;
	private final Validator validador;

	public SoporteRegistro(
			UsuarioDAO usuarioDAO,
			PasswordEncoder codificadorContrasena,
			PasswordPolicy politicaContrasenas,
			Validator validador) {
		this.usuarioDAO = usuarioDAO;
		this.codificadorContrasena = codificadorContrasena;
		this.politicaContrasenas = politicaContrasenas;
		this.validador = validador;
	}

	public void validarSolicitud(Object solicitud, String contrasena, String... datosPersonales) {
		if (solicitud == null) {
			throw new InvalidRegistrationException();
		}

		Map<String, List<String>> errores = new LinkedHashMap<>();
		for (ConstraintViolation<Object> incumplimiento : validador.validate(solicitud)) {
			errores.computeIfAbsent(
					incumplimiento.getPropertyPath().toString(),
					campo -> new ArrayList<>())
					.add(incumplimiento.getMessage());
		}
		if (!errores.isEmpty()) {
			throw new InvalidRegistrationException(errores);
		}

		List<String> datosNoNulos = Arrays.stream(datosPersonales)
				.filter(dato -> dato != null && !dato.isBlank())
				.toList();
		politicaContrasenas.validate(contrasena, datosNoNulos);
	}

	public String normalizarCorreo(String correo) {
		return correo.trim().toLowerCase(Locale.ROOT);
	}

	public void comprobarCorreoDisponible(String correo) {
		if (usuarioDAO.existsByEmail(correo)) {
			throw new InvalidRegistrationException(MENSAJE_DATOS_NO_VALIDOS);
		}
	}

	public String codificarContrasena(String contrasena) {
		return codificadorContrasena.encode(contrasena);
	}

	public <T> T guardarRegistro(Supplier<T> operacion) {
		try {
			return operacion.get();
		} catch (DuplicateKeyException excepcion) {
			throw new InvalidRegistrationException(MENSAJE_DATOS_NO_VALIDOS);
		}
	}
}