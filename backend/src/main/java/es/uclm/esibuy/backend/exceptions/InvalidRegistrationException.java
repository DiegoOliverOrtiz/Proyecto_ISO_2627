package es.uclm.esibuy.backend.exceptions;

public class InvalidRegistrationException extends RuntimeException {

	public InvalidRegistrationException() {
		super("Los datos de registro o la contraseña no cumplen los requisitos.");
	}
}