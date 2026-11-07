package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletarRecuperacionRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(max = 128) String nuevaContrasena) {
}