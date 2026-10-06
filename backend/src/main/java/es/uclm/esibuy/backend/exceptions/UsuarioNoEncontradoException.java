package es.uclm.esibuy.backend.exceptions;

public class UsuarioNoEncontradoException extends RuntimeException {

	public UsuarioNoEncontradoException(String id) {
		super("No se ha encontrado el usuario solicitado.");
	}
}
