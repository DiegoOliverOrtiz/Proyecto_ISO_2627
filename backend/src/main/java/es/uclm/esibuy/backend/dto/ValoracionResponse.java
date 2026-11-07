package es.uclm.esibuy.backend.dto;

import es.uclm.esibuy.backend.model.Valoracion;

public record ValoracionResponse(
        String id,
        String pedidoId,
        String productoId,
        int puntuacion,
        String comentario) {

    public static ValoracionResponse de(Valoracion valoracion) {
        return new ValoracionResponse(
                valoracion.id(),
                valoracion.pedidoId(),
                valoracion.productoId(),
                valoracion.puntuacion(),
                valoracion.comentario());
    }
}
