package es.uclm.esibuy.backend.exceptions;

public class CategoriaConProductosException extends RuntimeException {

    public CategoriaConProductosException() {
        super("No se puede eliminar una categoría con productos asociados.");
    }
}