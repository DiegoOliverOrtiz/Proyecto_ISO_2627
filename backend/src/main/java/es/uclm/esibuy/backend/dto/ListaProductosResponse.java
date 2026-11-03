package es.uclm.esibuy.backend.dto;

import java.util.List;

public record ListaProductosResponse(
        String id,
        String nombre,
        boolean predeterminada,
        List<ProductoFavoritoResponse> productos) {
}
