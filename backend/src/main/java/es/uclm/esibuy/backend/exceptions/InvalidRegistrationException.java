package es.uclm.esibuy.backend.exceptions;

import java.util.List;
import java.util.Map;

public class InvalidRegistrationException extends RuntimeException {

	private final Map<String, List<String>> fieldErrors;

	public InvalidRegistrationException() {
		this("Los datos de registro o la contraseña no cumplen los requisitos.", Map.of());
	}

	public InvalidRegistrationException(String field, String message) {
		this(message, Map.of(field, List.of(message)));
	}

	public InvalidRegistrationException(Map<String, List<String>> fieldErrors) {
		this("Los datos de registro no cumplen los requisitos.", fieldErrors);
	}

	private InvalidRegistrationException(String message, Map<String, List<String>> fieldErrors) {
		super(message);
		this.fieldErrors = fieldErrors;
	}

	public Map<String, List<String>> getFieldErrors() {
		return fieldErrors;
	}
}