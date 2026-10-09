package es.uclm.esibuy.backend.exceptions;

public class AutoBloqueoAdministradorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutoBloqueoAdministradorException() {
        super("Un administrador no puede bloquear ni desbloquear su propia cuenta.");
    }
}