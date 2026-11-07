package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotNull;

public record PreferenciaEmailOtpRequest(@NotNull Boolean activado) {
}
