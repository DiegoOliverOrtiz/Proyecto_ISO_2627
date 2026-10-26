package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// Solo incluye los campos modificables: el propietario (vendedor) y la visibilidad no se pueden cambiar aquí.
public record ModificarProductoRequest(
    @NotBlank(message = "El nombre del producto es obligatorio") String nombre,
    @NotBlank(message = "La categoría del producto es obligatoria") String categoria,
    String descripcion,
    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor que 0") Double precio,
    @PositiveOrZero(message = "El stock no puede ser negativo") int stock,
    @PositiveOrZero(message = "El descuento no puede ser negativo")
    @Max(value = 100, message = "El descuento no puede superar el 100 %") int descuento,
    @PositiveOrZero(message = "El descuento premium no puede ser negativo")
    @Max(value = 100, message = "El descuento premium no puede superar el 100 %") int descuentoPremium,
    // Si no se envía (null), se conserva el valor actual del producto.
    Boolean soloPremium
) implements DatosProductoRequest {
    public ModificarProductoRequest(
            String nombre,
            String categoria,
            String descripcion,
            Double precio,
            int stock,
            int descuento,
            int descuentoPremium) {
        this(nombre, categoria, descripcion, precio, stock, descuento, descuentoPremium, null);
    }
}
