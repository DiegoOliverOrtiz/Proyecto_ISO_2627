package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record FiltroProductosRequest(
        @Size(max = 100, message = "La búsqueda no puede superar 100 caracteres")
        String nombre,
        @Size(max = 100, message = "La categoría no puede superar 100 caracteres")
        String categoria,
        @DecimalMin(value = "0.0", message = "El precio mínimo no puede ser negativo") Double precioMinimo,
        @DecimalMin(value = "0.0", message = "El precio máximo no puede ser negativo") Double precioMaximo,
        Boolean disponible) {
}
