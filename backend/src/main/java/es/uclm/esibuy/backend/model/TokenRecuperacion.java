package es.uclm.esibuy.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tokens_recuperacion")
public class TokenRecuperacion {

    @Id
    private String id;

    @Indexed(unique = true)
    private String hashToken;

    @Indexed
    private String usuarioId;

    @Indexed(expireAfter = "0s")
    private Instant caducidad;

    private boolean consumido;

    public TokenRecuperacion() {
    }

    public TokenRecuperacion(String hashToken, String usuarioId, Instant caducidad) {
        this.hashToken = hashToken;
        this.usuarioId = usuarioId;
        this.caducidad = caducidad;
        this.consumido = false;
    }

    public String getId() {
        return id;
    }

    public String getHashToken() {
        return hashToken;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Instant getCaducidad() {
        return caducidad;
    }

    public boolean isConsumido() {
        return consumido;
    }
}