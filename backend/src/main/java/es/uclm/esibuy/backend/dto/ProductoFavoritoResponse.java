package es.uclm.esibuy.backend.dto;

public record ProductoFavoritoResponse(
        String id,
        String nombre,
        String imagen,
        Double precio,
        Integer stock,
        boolean disponible,
        String aviso) {
}
