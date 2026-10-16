package es.uclm.esibuy.backend.exceptions;

public class CategoriaNoEncontradaException extends RuntimeException {

    public CategoriaNoEncontradaException() {
        super("No se ha encontrado la categoría solicitada.");
    }
}
