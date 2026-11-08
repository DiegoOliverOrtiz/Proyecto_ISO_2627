package es.uclm.esibuy.backend.exceptions;

public class ProductoAjenoException extends RuntimeException {
    public ProductoAjenoException() {
        super("No puedes eliminar productos de otro vendedor.");
    }
}
