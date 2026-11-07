package es.uclm.esibuy.backend.dto;

import java.util.List;

public record TwoFactorEnrollmentLoginResponse(
        LoginResponse login,
        List<String> backupCodes) {

    @Override
    public String toString() {
        return "TwoFactorEnrollmentLoginResponse[login="
                + login + ", backupCodes=REDACTED]";
    }
}
