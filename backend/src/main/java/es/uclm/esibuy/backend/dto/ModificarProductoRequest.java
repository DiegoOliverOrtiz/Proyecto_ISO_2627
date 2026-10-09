package es.uclm.esibuy.backend.dto;

// Solo incluye los campos modificables: el propietario (vendedor) y la visibilidad no se pueden cambiar aquí.
public record ModificarProductoRequest(
    String nombre,
    String categoria,
    String descripcion,
    Double precio,
    int stock,
    int descuento,
    int descuentoPremium
) {
}
