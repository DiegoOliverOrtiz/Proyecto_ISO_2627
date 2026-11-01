package es.uclm.esibuy.backend.exceptions;

public class DireccionDuplicadaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DireccionDuplicadaException() {
		super("Ya tienes una dirección con ese nombre.");
	}
}