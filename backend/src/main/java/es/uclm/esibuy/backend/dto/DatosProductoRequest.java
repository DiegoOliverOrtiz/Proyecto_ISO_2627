package es.uclm.esibuy.backend.dto;

// Datos comunes a la creación (HU17) y a la modificación (HU18) de un producto.
public interface DatosProductoRequest {
    String nombre();

    String categoria();

    String descripcion();

    Double precio();

    int stock();

    int descuento();

    int descuentoPremium();
}
