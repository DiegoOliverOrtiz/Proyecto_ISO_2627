package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NombreListaRequest(
        @NotBlank(message = "El nombre de la lista es obligatorio.")
        @Size(max = 50, message = "El nombre de la lista no puede superar 50 caracteres.")
        String nombre) {
}
