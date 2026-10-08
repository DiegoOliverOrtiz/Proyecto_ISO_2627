package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CrearProductoRequest(
    @NotBlank String nombre,
    @NotBlank String categoria,
    String descripcion,
    Boolean esVisible,
    @NotNull @Positive Double precio,
    @PositiveOrZero int stock,
    @PositiveOrZero int descuento,
    @PositiveOrZero int descuentoPremium
) {
    public CrearProductoRequest(
            String nombre,
            String categoria,
            String descripcion,
            Double precio,
            int stock,
            int descuento,
            int descuentoPremium) {
        this(nombre, categoria, descripcion, true, precio, stock, descuento, descuentoPremium);
    }
}
