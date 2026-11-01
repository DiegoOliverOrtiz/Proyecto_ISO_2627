package es.uclm.esibuy.backend.exceptions;

public class MetodoPagoDuplicadoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MetodoPagoDuplicadoException() {
		super("Ya tienes un método de pago con ese nombre.");
	}
}