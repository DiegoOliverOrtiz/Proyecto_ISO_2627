package es.uclm.esibuy.backend.dao;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptStore {
    private static final String COLLECTION = "seguridad_login";
    private static final int WINDOW_SECONDS = 300;
    private static final int ACCOUNT_FAILURE_WINDOW_SECONDS = 600;
    private static final int MAX_PAIR_ATTEMPTS = 5;
    private static final int ADAPTIVE_USER_THRESHOLD = 20;
    private static final int ADAPTIVE_DELAY_SECONDS = 2;
    private static final int[] BACKOFF_SECONDS = { 30, 120, 240, 300 };

    private final MongoTemplate mongoTemplate;
    private final String pepper;
    private final AtomicBoolean ttlIndexReady = new AtomicBoolean();

    public LoginAttemptStore(MongoTemplate mongoTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.security.password.pepper:}") String pepper) {
        this.mongoTemplate = mongoTemplate;
        this.pepper = pepper;
    }

    public int reservarIntento(String ip, String email, Instant now) {
        ensureTtlIndex();
        String accountId = id("cuenta", email);
        int accountWait = esperaBloqueoCuenta(accountId, now);
        if (accountWait > 0) {
            return accountWait;
        }

        String emailKey = fingerprint(email);
        String pairId = "par:" + fingerprint(ip + "\u0000" + email);
        int pairWait = reservarVentana(pairId, now, MAX_PAIR_ATTEMPTS);
        if (pairWait > 0) {
            return pairWait;
        }

        String ipId = id("ip", ip);
        int distinctUsers = registrarUsuarioIp(ipId, emailKey, now);
        if (distinctUsers >= ADAPTIVE_USER_THRESHOLD) {
            int adaptiveWait = reservarCupoIpAdaptativo(ipId, now);
            if (adaptiveWait > 0) {
                return adaptiveWait;
            }
            return reservarRetrasoAdaptativo(ipId, now);
        }
        return 0;
    }

    public int registrarFallo(String email, Instant now) {
        ensureTtlIndex();
        String accountId = id("cuenta", email);
        prepararVentana(accountId, now, ACCOUNT_FAILURE_WINDOW_SECONDS, true);
        Query activeWindow = Query.query(Criteria.where("_id").is(accountId)
                .and("windowStart").gt(Date.from(now.minusSeconds(ACCOUNT_FAILURE_WINDOW_SECONDS))));
        mongoTemplate.updateFirst(activeWindow, new Update().inc("failures", 1), COLLECTION);

        Document account = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(accountId)),
                Document.class, COLLECTION);
        int failures = account == null ? 0 : account.getInteger("failures", 0);
        if (failures < MAX_PAIR_ATTEMPTS) {
            return 0;
        }

        int step = Math.min(account.getInteger("backoffStep", 0), BACKOFF_SECONDS.length - 1);
        Query lockable = Query.query(Criteria.where("_id").is(accountId)
                .and("backoffStep").is(account.getInteger("backoffStep", 0))
                .and("failures").gte(MAX_PAIR_ATTEMPTS)
                .andOperator(new Criteria().orOperator(
                        Criteria.where("lockUntil").exists(false),
                        Criteria.where("lockUntil").lte(Date.from(now)))));
        Instant lockUntil = now.plusSeconds(BACKOFF_SECONDS[step]);
        long updated = mongoTemplate.updateFirst(lockable,
                new Update().set("lockUntil", Date.from(lockUntil)).inc("backoffStep", 1)
                        .set("expiresAt", Date.from(now.plus(Duration.ofDays(1)))),
                COLLECTION).getModifiedCount();
        if (updated == 1) {
            return BACKOFF_SECONDS[step];
        }
        return esperaBloqueoCuenta(accountId, now);
    }

    public void registrarExito(String email, Instant now) {
        ensureTtlIndex();
        Query account = Query.query(Criteria.where("_id").is(id("cuenta", email)));
        mongoTemplate.updateFirst(account,
                new Update().set("windowStart", Date.from(now)).set("failures", 0)
                        .set("backoffStep", 0).unset("lockUntil")
                        .set("expiresAt", Date.from(now.plus(Duration.ofDays(1)))),
                COLLECTION);
    }

    private int reservarVentana(String documentId, Instant now, int limit) {
        prepararVentana(documentId, now, WINDOW_SECONDS, false);
        Query withinWindow = Query.query(Criteria.where("_id").is(documentId)
                .and("windowStart").gt(Date.from(now.minusSeconds(WINDOW_SECONDS)))
                .and("attempts").lt(limit));
        if (mongoTemplate.updateFirst(withinWindow, new Update().inc("attempts", 1), COLLECTION)
                .getModifiedCount() == 1) {
            return 0;
        }
        Document window = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(documentId)),
                Document.class, COLLECTION);
        return window == null ? WINDOW_SECONDS
                : segundosRestantes(window.getDate("windowStart").toInstant().plusSeconds(WINDOW_SECONDS), now);
    }

    private int registrarUsuarioIp(String ipId, String emailKey, Instant now) {
        prepararVentana(ipId, now, WINDOW_SECONDS, false);
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        List<String> users = ipState == null ? List.of() : ipState.getList("users", String.class, List.of());
        if (!users.contains(emailKey) && users.size() < ADAPTIVE_USER_THRESHOLD) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(ipId)),
                    new Update().addToSet("users", emailKey), COLLECTION);
        }
        ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        return ipState == null ? 0 : ipState.getList("users", String.class, List.of()).size();
    }

    private int reservarRetrasoAdaptativo(String ipId, Instant now) {
        Query ready = Query.query(Criteria.where("_id").is(ipId)
                .andOperator(new Criteria().orOperator(
                        Criteria.where("nextAllowedAt").exists(false),
                        Criteria.where("nextAllowedAt").lte(Date.from(now)))));
        long updated = mongoTemplate.updateFirst(ready,
                new Update().set("nextAllowedAt", Date.from(now.plusSeconds(ADAPTIVE_DELAY_SECONDS))),
                COLLECTION).getModifiedCount();
        if (updated == 1) {
            return 0;
        }
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        return ipState == null ? ADAPTIVE_DELAY_SECONDS
                : segundosRestantes(ipState.getDate("nextAllowedAt").toInstant(), now);
    }

    private int reservarCupoIpAdaptativo(String ipId, Instant now) {
        Query underLimit = Query.query(Criteria.where("_id").is(ipId)
                .and("adaptiveAttempts").lt(2));
        if (mongoTemplate.updateFirst(underLimit, new Update().inc("adaptiveAttempts", 1), COLLECTION)
                .getModifiedCount() == 1) {
            return 0;
        }
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        if (ipState == null || ipState.getDate("windowStart") == null) {
            return WINDOW_SECONDS;
        }
        return segundosRestantes(ipState.getDate("windowStart").toInstant().plusSeconds(WINDOW_SECONDS), now);
    }

    private int esperaBloqueoCuenta(String accountId, Instant now) {
        Document account = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(accountId)),
                Document.class, COLLECTION);
        if (account == null || account.getDate("lockUntil") == null) {
            return 0;
        }
        return segundosRestantes(account.getDate("lockUntil").toInstant(), now);
    }

    private void ensureTtlIndex() {
        if (ttlIndexReady.compareAndSet(false, true)) {
            try {
                mongoTemplate.indexOps(COLLECTION)
                        .createIndex(new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                                .expire(Duration.ZERO));
            } catch (RuntimeException exception) {
                ttlIndexReady.set(false);
                throw exception;
            }
        }
    }

    private void prepararVentana(String documentId, Instant now, int windowSeconds, boolean preserveBackoff) {
        Query identity = Query.query(Criteria.where("_id").is(documentId));
        Date expiry = Date.from(now.plus(Duration.ofDays(1)));
        try {
            mongoTemplate.upsert(identity, new Update()
                    .setOnInsert("windowStart", Date.from(now))
                    .setOnInsert("attempts", 0)
                    .setOnInsert("adaptiveAttempts", 0)
                    .setOnInsert("failures", 0)
                    .setOnInsert("backoffStep", 0)
                    .setOnInsert("users", new ArrayList<String>())
                    .setOnInsert("expiresAt", expiry), COLLECTION);
        } catch (DuplicateKeyException ignored) {
            // Another request created the window; subsequent atomic updates decide the winner.
        }
        Query expired = Query.query(Criteria.where("_id").is(documentId)
                .and("windowStart").lte(Date.from(now.minusSeconds(windowSeconds))));
        Update reset = new Update().set("windowStart", Date.from(now)).set("expiresAt", expiry);
        if (preserveBackoff) {
            reset.set("failures", 0);
        } else {
                reset.set("attempts", 0).set("adaptiveAttempts", 0).set("users", List.of())
                    .unset("nextAllowedAt");
        }
        mongoTemplate.updateFirst(expired, reset, COLLECTION);
    }

    private String id(String type, String value) {
        return type + ":" + fingerprint(value);
    }

    private String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo seudonimizar el estado de intentos.", exception);
        }
    }

    private static int segundosRestantes(Instant until, Instant now) {
        return until.isAfter(now) ? (int) Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000) : 0;
    }
}