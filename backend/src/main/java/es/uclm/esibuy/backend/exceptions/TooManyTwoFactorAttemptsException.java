package es.uclm.esibuy.backend.exceptions;

public class TooManyTwoFactorAttemptsException extends RuntimeException {
    public TooManyTwoFactorAttemptsException() {
        super("Has alcanzado el límite de intentos. Espera 5 minutos antes de volver a intentarlo.");
    }
}
