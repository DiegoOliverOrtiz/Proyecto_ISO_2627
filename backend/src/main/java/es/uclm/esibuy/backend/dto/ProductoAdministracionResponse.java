package es.uclm.esibuy.backend.dto;

// HU-34: la misma información que ve el vendedor de sus productos, más el vendedor asociado.
public record ProductoAdministracionResponse(
        String id,
        String nombre,
        String categoria,
        int stock,
        String descripcion,
        Double precio,
        int descuento,
        int descuentoPremium,
        boolean esVisible,
        String imagen,
        boolean soloPremium,
        String vendedor) {
}
