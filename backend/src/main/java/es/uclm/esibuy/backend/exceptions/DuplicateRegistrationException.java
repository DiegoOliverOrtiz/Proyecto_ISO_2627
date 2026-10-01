package es.uclm.esibuy.backend.exceptions;

public class DuplicateRegistrationException extends RuntimeException {

	public DuplicateRegistrationException() {
		super("Ya existe un usuario con los datos únicos indicados.");
	}
}