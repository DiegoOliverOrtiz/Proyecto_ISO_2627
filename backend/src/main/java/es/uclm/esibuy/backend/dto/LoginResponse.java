package es.uclm.esibuy.backend.dto;

public record LoginResponse(String email, String role, boolean requiresTwoFactor, String twoFactorToken) {

    public LoginResponse(String email, String role) {
        this(email, role, false, null);
    }
}
