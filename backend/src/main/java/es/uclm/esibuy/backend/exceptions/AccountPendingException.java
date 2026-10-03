package es.uclm.esibuy.backend.exceptions;

public class AccountPendingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AccountPendingException() {
		super("Tu cuenta está pendiente de revisión.");
	}
}
