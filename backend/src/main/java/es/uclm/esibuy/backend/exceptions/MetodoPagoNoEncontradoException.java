package es.uclm.esibuy.backend.exceptions;

public class MetodoPagoNoEncontradoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MetodoPagoNoEncontradoException() {
		super("No se encontró el método de pago indicado.");
	}
}