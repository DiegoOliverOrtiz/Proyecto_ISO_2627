package es.uclm.esibuy.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "valoraciones")
@CompoundIndex(
        name = "uq_valoracion_cliente_producto",
        def = "{'clienteId': 1, 'productoId': 1}",
        unique = true)
public record Valoracion(
        @Id String id,
        String clienteId,
        String pedidoId,
        String productoId,
        int puntuacion,
        String comentario) {

    public Valoracion(
            String clienteId,
            String pedidoId,
            String productoId,
            int puntuacion,
            String comentario) {
        this(null, clienteId, pedidoId, productoId, puntuacion, comentario);
    }
}
