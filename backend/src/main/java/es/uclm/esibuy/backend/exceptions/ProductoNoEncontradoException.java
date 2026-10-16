package es.uclm.esibuy.backend.exceptions;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException() {
        super("No se ha encontrado el producto solicitado.");
    }
}
