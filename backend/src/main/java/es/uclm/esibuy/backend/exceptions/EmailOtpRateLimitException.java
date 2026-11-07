package es.uclm.esibuy.backend.exceptions;

public class EmailOtpRateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public EmailOtpRateLimitException(int retryAfterSeconds) {
        super("Se ha alcanzado el límite de envíos de códigos. Espera antes de solicitar otro.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
