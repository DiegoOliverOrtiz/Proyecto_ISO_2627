package es.uclm.esibuy.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrearValoracionRequest(
        @NotNull @Min(1) @Max(5) Integer puntuacion,
        @Size(max = 500) String comentario) {
}
