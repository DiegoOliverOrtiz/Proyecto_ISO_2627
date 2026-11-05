package es.uclm.esibuy.backend.exceptions;

public class TokenRecuperacionInvalidoException extends RuntimeException {
    public TokenRecuperacionInvalidoException() {
        super("El enlace de recuperación no es válido o ha caducado.");
    }
}