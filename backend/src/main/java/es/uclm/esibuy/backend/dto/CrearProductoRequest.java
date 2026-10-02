package es.uclm.esibuy.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearProductoRequest(
    @NotBlank String nombre,
    @NotBlank String categoria,
    String descripcion,
    double precio,
    int stock,
    int descuento,
    int descuentoPremium,
    String vendedor
) {


}
