package es.uclm.esibuy.backend.dto;

public record LoginResponse(
        String email,
        String role,
        boolean requiresTwoFactor,
        String twoFactorToken,
        boolean requiresTwoFactorSetup,
        boolean requiresEmailVerification,
        boolean emailVerificationEmailSent) {

    public LoginResponse(String email, String role) {
        this(email, role, false, null, false, false, false);
    }

    public LoginResponse(
            String email,
            String role,
            boolean requiresTwoFactor,
            String twoFactorToken) {
        this(email, role, requiresTwoFactor, twoFactorToken, false, false, false);
    }

    public LoginResponse(
            String email,
            String role,
            boolean requiresTwoFactor,
            String twoFactorToken,
            boolean requiresTwoFactorSetup) {
        this(email, role, requiresTwoFactor, twoFactorToken, requiresTwoFactorSetup, false, false);
    }
}
