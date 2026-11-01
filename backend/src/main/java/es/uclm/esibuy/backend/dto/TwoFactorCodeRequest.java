package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorCodeRequest(@NotBlank @Size(max = 64) String code) {
    @Override
    public String toString() {
        return "TwoFactorCodeRequest[code=REDACTED]";
    }
}
