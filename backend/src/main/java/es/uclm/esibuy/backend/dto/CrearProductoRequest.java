package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// El vendedor no forma parte de la petición: siempre es el de la sesión autenticada.
public record CrearProductoRequest(
    @NotBlank String nombre,
    @NotBlank String categoria,
    String descripcion,
    Boolean esVisible,
    @NotNull @Positive Double precio,
    @Positive(message = "El stock debe ser mayor que 0") int stock,
    @PositiveOrZero @Max(100) int descuento,
    @PositiveOrZero @Max(100) int descuentoPremium,
    Boolean soloPremium
) implements DatosProductoRequest {
    public CrearProductoRequest(
            String nombre,
            String categoria,
            String descripcion,
            Double precio,
            int stock,
            int descuento,
            int descuentoPremium) {
        this(nombre, categoria, descripcion, true, precio, stock, descuento, descuentoPremium, false);
    }
}
