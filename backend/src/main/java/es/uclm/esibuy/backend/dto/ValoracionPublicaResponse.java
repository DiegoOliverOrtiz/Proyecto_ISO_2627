package es.uclm.esibuy.backend.dto;

import es.uclm.esibuy.backend.model.Valoracion;

public record ValoracionPublicaResponse(int puntuacion, String comentario) {
    public static ValoracionPublicaResponse de(Valoracion valoracion) {
        return new ValoracionPublicaResponse(valoracion.puntuacion(), valoracion.comentario());
    }
}
