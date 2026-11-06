package es.uclm.esibuy.backend.services;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.LoginAttemptStore;
import es.uclm.esibuy.backend.exceptions.LoginRateLimitException;

@Service
public class LoginAttemptService {
    private final LoginAttemptStore store;

    public LoginAttemptService(LoginAttemptStore store) {
        this.store = store;
    }

    public void reservarIntento(String ip, String email) {
        int retryAfter = store.reservarIntento(ip, normalizar(email), Instant.now());
        if (retryAfter > 0) {
            throw new LoginRateLimitException(retryAfter);
        }
    }

    public int registrarFallo(String email) {
        return store.registrarFallo(normalizar(email), Instant.now());
    }

    public void registrarExito(String email) {
        store.registrarExito(normalizar(email), Instant.now());
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}