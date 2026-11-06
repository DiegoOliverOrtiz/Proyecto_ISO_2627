package es.uclm.esibuy.backend.exceptions;

public class LoginRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;

    public LoginRateLimitException(int retryAfterSeconds) {
        super("Demasiados intentos de inicio de sesión. Espera antes de volver a intentarlo.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}