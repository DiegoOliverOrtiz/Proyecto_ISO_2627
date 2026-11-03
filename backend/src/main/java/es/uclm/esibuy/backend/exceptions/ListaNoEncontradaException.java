package es.uclm.esibuy.backend.exceptions;

public class ListaNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListaNoEncontradaException() {
        super("No se ha encontrado la lista solicitada.");
    }
}
