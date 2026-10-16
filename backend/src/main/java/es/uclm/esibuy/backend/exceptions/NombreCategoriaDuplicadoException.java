package es.uclm.esibuy.backend.exceptions;

public class NombreCategoriaDuplicadoException extends RuntimeException {

    public NombreCategoriaDuplicadoException() {
        super("Ya existe una categoría con ese nombre.");
    }
}
