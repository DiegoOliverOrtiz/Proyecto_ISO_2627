package es.uclm.esibuy.backend.dto;

import java.util.List;

public record ResumenListaProductosResponse(
        String id,
        String nombre,
        boolean predeterminada,
        List<String> productoIds) {
}
