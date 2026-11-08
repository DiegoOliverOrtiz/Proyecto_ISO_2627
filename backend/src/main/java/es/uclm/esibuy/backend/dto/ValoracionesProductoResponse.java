package es.uclm.esibuy.backend.dto;

import java.util.List;

public record ValoracionesProductoResponse(
        double media,
        int total,
        List<ValoracionPublicaResponse> valoraciones) {
}
