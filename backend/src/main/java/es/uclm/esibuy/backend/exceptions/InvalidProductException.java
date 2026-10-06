package es.uclm.esibuy.backend.exceptions;

public class InvalidProductException extends RuntimeException {

    public InvalidProductException() {
		super("Los datos del producto no cumplen con los requisitos.");
	}
}
