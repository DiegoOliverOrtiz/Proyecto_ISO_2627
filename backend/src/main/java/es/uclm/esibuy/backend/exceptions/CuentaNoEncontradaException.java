package es.uclm.esibuy.backend.exceptions;

public class CuentaNoEncontradaException extends RuntimeException {
    public CuentaNoEncontradaException() {
        super("No se ha encontrado la cuenta autenticada.");
    }
}
