package es.uclm.esibuy.backend.exceptions;

public class UsuarioNoEncontradoException extends RuntimeException {

	public UsuarioNoEncontradoException() {
		super("No se encontró el usuario solicitado.");
	}
	
	public UsuarioNoEncontradoException(String id) {
		super("No se ha encontrado el usuario solicitado.");
	}
}
