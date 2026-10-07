package es.uclm.esibuy.backend.exceptions;

public class UltimoAdministradorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UltimoAdministradorException() {
		super("No se puede eliminar al último administrador del sistema.");
	}
}