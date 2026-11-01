package es.uclm.esibuy.backend.dto;

import java.util.List;

public record TwoFactorConfirmationResponse(boolean enabled, List<String> backupCodes) {
    @Override
    public String toString() {
        return "TwoFactorConfirmationResponse[enabled=" + enabled + ", backupCodes=REDACTED]";
    }
}
