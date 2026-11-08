package es.uclm.esibuy.backend.dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Estado separado del perfil: guardar un Usuario antiguo no restaura códigos consumidos. */
@Repository
public class TotpSecurityStore {
    private static final String COLLECTION = "seguridad_totp";
    public static final int MAX_ATTEMPTS = 5;
    public static final int WINDOW_SECONDS = 300;
    private static final String CAMPO_INICIO = "inicio";
    private static final String CAMPO_INTENTOS = "intentos";
    private static final String CAMPO_ULTIMO_INTERVALO = "ultimoIntervalo";
    private final MongoTemplate mongoTemplate;

    public TotpSecurityStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean reservarIntento(String userId, Instant now) {
        String id = "intentos:" + userId;
        Query identity = Query.query(Criteria.where("_id").is(id));
        try {
            mongoTemplate.upsert(identity, new Update()
                    .setOnInsert(CAMPO_INICIO, Date.from(now)).setOnInsert(CAMPO_INTENTOS, 0), COLLECTION);
        } catch (DuplicateKeyException ignored) {
            // Otra instancia ha creado el contador al mismo tiempo.
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)
                        .and(CAMPO_INICIO).lte(Date.from(now.minusSeconds(WINDOW_SECONDS)))),
                new Update().set(CAMPO_INICIO, Date.from(now)).set(CAMPO_INTENTOS, 0), COLLECTION);
        return mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)
                        .and(CAMPO_INTENTOS).lt(MAX_ATTEMPTS)),
                new Update().inc(CAMPO_INTENTOS, 1), COLLECTION).getModifiedCount() == 1;
    }

    public boolean consumirIntervalo(String userId, String secret, long step) {
        String id = "totp:" + userId + ":" + fingerprint(secret);
        try {
            mongoTemplate.upsert(Query.query(Criteria.where("_id").is(id)),
                    new Update().setOnInsert(CAMPO_ULTIMO_INTERVALO, -1L), COLLECTION);
        } catch (DuplicateKeyException ignored) {
            // La condición de la actualización decide cuál de las peticiones gana.
        }
        return mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)
                        .and(CAMPO_ULTIMO_INTERVALO).lt(step)),
                new Update().set(CAMPO_ULTIMO_INTERVALO, step), COLLECTION).getModifiedCount() == 1;
    }

    public boolean consumirRespaldo(String userId, String hash) {
        try {
            // El índice único de _id garantiza un solo consumo, incluso entre servidores.
            mongoTemplate.insert(new Document("_id", "respaldo:" + userId + ":" + fingerprint(hash)), COLLECTION);
            return true;
        } catch (DuplicateKeyException alreadyUsed) {
            return false;
        }
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
