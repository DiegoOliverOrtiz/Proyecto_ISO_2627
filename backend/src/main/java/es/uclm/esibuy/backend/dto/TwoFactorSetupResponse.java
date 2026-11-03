package es.uclm.esibuy.backend.dto;

public record TwoFactorSetupResponse(String otpauthUri, String qrDataUrl, boolean enabled) {

    @Override
    public String toString() {
        return "TwoFactorSetupResponse[enabled=" + enabled + ", secrets=REDACTED]";
    }

    public TwoFactorSetupResponse(String otpauthUri, boolean enabled) {
        this(otpauthUri, null, enabled);
    }
}
