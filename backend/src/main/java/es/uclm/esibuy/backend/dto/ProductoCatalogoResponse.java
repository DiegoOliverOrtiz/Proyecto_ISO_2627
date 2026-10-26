package es.uclm.esibuy.backend.dto;

public record ProductoCatalogoResponse(
        String id,
        String nombre,
        String categoria,
        int stock,
        Double precio,
        Double precioOriginal,
        int descuento,
        int descuentoPremium,
        int descuentoAplicado,
        String descripcion,
        String imagen,
        String vendedor,
        boolean esVisible,
        boolean soloPremium,
        boolean puedeConsultarDetalle) {
}
