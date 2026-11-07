package es.uclm.esibuy.backend.dto;

public record LoginResponse(
        String email,
        String role,
        boolean requiresTwoFactor,
        String twoFactorToken,
        boolean requiresTwoFactorSetup) {

    public LoginResponse(String email, String role) {
        this(email, role, false, null, false);
    }

    public LoginResponse(
            String email,
            String role,
            boolean requiresTwoFactor,
            String twoFactorToken) {
        this(email, role, requiresTwoFactor, twoFactorToken, false);
    }
}
