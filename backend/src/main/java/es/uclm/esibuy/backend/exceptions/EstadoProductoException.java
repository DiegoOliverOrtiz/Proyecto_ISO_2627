package es.uclm.esibuy.backend.exceptions;

public class EstadoProductoException extends RuntimeException {

    public EstadoProductoException(boolean activo) {
        super(activo ? "El producto ya está activo." : "El producto ya está desactivado.");
    }
}
