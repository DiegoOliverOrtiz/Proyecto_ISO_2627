package es.uclm.esibuy.backend.exceptions;

public class NombreListaDuplicadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NombreListaDuplicadoException() {
        super("Ya tienes una lista con ese nombre.");
    }
}
