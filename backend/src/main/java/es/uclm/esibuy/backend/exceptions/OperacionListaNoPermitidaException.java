package es.uclm.esibuy.backend.exceptions;

public class OperacionListaNoPermitidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperacionListaNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
