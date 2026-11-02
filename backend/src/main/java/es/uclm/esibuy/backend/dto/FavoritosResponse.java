package es.uclm.esibuy.backend.dto;

import java.util.List;

public record FavoritosResponse(
        List<String> ids,
        List<ProductoFavoritoResponse> productos) {
}
