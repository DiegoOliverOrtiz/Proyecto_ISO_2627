package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearCategoriaRequest(
        @NotBlank(message = "El nombre de la categoría no puede estar vacío")
        String nombre,
        @NotBlank(message = "La descripción de la categoría no puede estar vacía")
        String descripcion
) {
}