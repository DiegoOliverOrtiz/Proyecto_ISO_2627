package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarTipoClienteRequest(
        @NotNull(message = "El tipo de cliente es obligatorio.") Boolean esPremium) {
}
