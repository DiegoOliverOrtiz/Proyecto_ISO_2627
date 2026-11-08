package es.uclm.esibuy.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ModificarCantidadCarritoRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 0) @DecimalMax("2147483647") BigDecimal cantidad) {
}
