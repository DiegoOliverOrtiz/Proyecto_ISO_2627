package es.uclm.esibuy.backend.exceptions;

public class AutoEliminacionAdministradorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AutoEliminacionAdministradorException() {
		super("Un administrador no puede eliminar su propia cuenta.");
	}
}