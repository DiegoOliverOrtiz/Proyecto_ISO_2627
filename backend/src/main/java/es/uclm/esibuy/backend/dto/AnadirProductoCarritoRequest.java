package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AnadirProductoCarritoRequest(
        @NotBlank String productoId,
        @NotNull @Positive Integer cantidad) {
}
