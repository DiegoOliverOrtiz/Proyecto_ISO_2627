package es.uclm.esibuy.backend.exceptions;

public class DireccionNoEncontradaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DireccionNoEncontradaException() {
		super("No se encontró la dirección indicada.");
	}
}