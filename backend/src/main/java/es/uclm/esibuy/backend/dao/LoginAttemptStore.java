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
    private static final int EMAIL_OTP_WINDOW_SECONDS = 600;
    private static final int MAX_EMAIL_OTP_SENDS = 5;
    private static final int ACCOUNT_FAILURE_WINDOW_SECONDS = 600;
    private static final int MAX_PAIR_ATTEMPTS = 5;
    private static final int ADAPTIVE_USER_THRESHOLD = 20;
    private static final int ADAPTIVE_DELAY_SECONDS = 2;
    private static final int[] BACKOFF_SECONDS = { 30, 120, 240, 300 };
    private static final String TIPO_CUENTA = "cuenta";
    private static final String CAMPO_WINDOW_START = "windowStart";
    private static final String CAMPO_FAILURES = "failures";
    private static final String CAMPO_BACKOFF_STEP = "backoffStep";
    private static final String CAMPO_LOCK_UNTIL = "lockUntil";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_ATTEMPTS = "attempts";
    private static final String CAMPO_USERS = "users";
    private static final String CAMPO_NEXT_ALLOWED_AT = "nextAllowedAt";
    private static final String CAMPO_ADAPTIVE_ATTEMPTS = "adaptiveAttempts";

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
        String accountId = id(TIPO_CUENTA, email);
        int accountWait = esperaBloqueoCuenta(accountId, now);
        if (accountWait > 0) {
            return accountWait;
        }

        String emailKey = fingerprint(email);
        String pairId = "par:" + fingerprint(ip + "\u0000" + email);
        int pairWait = reservarVentana(pairId, now, WINDOW_SECONDS, MAX_PAIR_ATTEMPTS);
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

    public int reservarEnvioOtp(String email, Instant now) {
        ensureTtlIndex();
        return reservarVentana(
                id("otp-email", email.trim().toLowerCase(java.util.Locale.ROOT)),
                now,
                EMAIL_OTP_WINDOW_SECONDS,
                MAX_EMAIL_OTP_SENDS);
    }

    public int registrarFallo(String email, Instant now) {
        ensureTtlIndex();
        String accountId = id(TIPO_CUENTA, email);
        prepararVentana(accountId, now, ACCOUNT_FAILURE_WINDOW_SECONDS, true);
        Query activeWindow = Query.query(Criteria.where("_id").is(accountId)
                .and(CAMPO_WINDOW_START).gt(Date.from(now.minusSeconds(ACCOUNT_FAILURE_WINDOW_SECONDS))));
        mongoTemplate.updateFirst(activeWindow, new Update().inc(CAMPO_FAILURES, 1), COLLECTION);

        Document account = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(accountId)),
                Document.class, COLLECTION);
        int failures = account == null ? 0 : account.getInteger(CAMPO_FAILURES, 0);
        if (failures < MAX_PAIR_ATTEMPTS) {
            return 0;
        }

        int step = Math.min(account.getInteger(CAMPO_BACKOFF_STEP, 0), BACKOFF_SECONDS.length - 1);
        Query lockable = Query.query(Criteria.where("_id").is(accountId)
                .and(CAMPO_BACKOFF_STEP).is(account.getInteger(CAMPO_BACKOFF_STEP, 0))
                .and(CAMPO_FAILURES).gte(MAX_PAIR_ATTEMPTS)
                .andOperator(new Criteria().orOperator(
                        Criteria.where(CAMPO_LOCK_UNTIL).exists(false),
                        Criteria.where(CAMPO_LOCK_UNTIL).lte(Date.from(now)))));
        Instant lockUntil = now.plusSeconds(BACKOFF_SECONDS[step]);
        long updated = mongoTemplate.updateFirst(lockable,
                new Update().set(CAMPO_LOCK_UNTIL, Date.from(lockUntil)).inc(CAMPO_BACKOFF_STEP, 1)
                        .set(CAMPO_EXPIRES_AT, Date.from(now.plus(Duration.ofDays(1)))),
                COLLECTION).getModifiedCount();
        if (updated == 1) {
            return BACKOFF_SECONDS[step];
        }
        return esperaBloqueoCuenta(accountId, now);
    }

    public void registrarExito(String email, Instant now) {
        ensureTtlIndex();
        Query account = Query.query(Criteria.where("_id").is(id(TIPO_CUENTA, email)));
        mongoTemplate.updateFirst(account,
                new Update().set(CAMPO_WINDOW_START, Date.from(now)).set(CAMPO_FAILURES, 0)
                        .set(CAMPO_BACKOFF_STEP, 0).unset(CAMPO_LOCK_UNTIL)
                        .set(CAMPO_EXPIRES_AT, Date.from(now.plus(Duration.ofDays(1)))),
                COLLECTION);
    }

    private int reservarVentana(String documentId, Instant now, int windowSeconds, int limit) {
        prepararVentana(documentId, now, windowSeconds, false);
        Query withinWindow = Query.query(Criteria.where("_id").is(documentId)
                .and(CAMPO_WINDOW_START)
                .gt(Date.from(now.minusSeconds(windowSeconds)))
                .and(CAMPO_ATTEMPTS).lt(limit));

        if (mongoTemplate.updateFirst(
                withinWindow,
                new Update().inc(CAMPO_ATTEMPTS, 1),
                COLLECTION)
                .getModifiedCount() == 1) {
            return 0;
        }
        Document window = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(documentId)),
                Document.class, COLLECTION);
        return window == null ? windowSeconds
                : segundosRestantes(
                        window.getDate(CAMPO_WINDOW_START)
                                .toInstant()
                                .plusSeconds(windowSeconds),
                        now);
    }

    private int registrarUsuarioIp(String ipId, String emailKey, Instant now) {
        prepararVentana(ipId, now, WINDOW_SECONDS, false);
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        List<String> users = ipState == null ? List.of() : ipState.getList(CAMPO_USERS, String.class, List.of());
        if (!users.contains(emailKey) && users.size() < ADAPTIVE_USER_THRESHOLD) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(ipId)),
                    new Update().addToSet(CAMPO_USERS, emailKey), COLLECTION);
        }
        ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        return ipState == null ? 0 : ipState.getList(CAMPO_USERS, String.class, List.of()).size();
    }

    private int reservarRetrasoAdaptativo(String ipId, Instant now) {
        Query ready = Query.query(Criteria.where("_id").is(ipId)
                .andOperator(new Criteria().orOperator(
                        Criteria.where(CAMPO_NEXT_ALLOWED_AT).exists(false),
                        Criteria.where(CAMPO_NEXT_ALLOWED_AT).lte(Date.from(now)))));
        long updated = mongoTemplate.updateFirst(ready,
                new Update().set(CAMPO_NEXT_ALLOWED_AT, Date.from(now.plusSeconds(ADAPTIVE_DELAY_SECONDS))),
                COLLECTION).getModifiedCount();
        if (updated == 1) {
            return 0;
        }
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        return ipState == null ? ADAPTIVE_DELAY_SECONDS
                : segundosRestantes(ipState.getDate(CAMPO_NEXT_ALLOWED_AT).toInstant(), now);
    }

    private int reservarCupoIpAdaptativo(String ipId, Instant now) {
        Query underLimit = Query.query(Criteria.where("_id").is(ipId)
                .and(CAMPO_ADAPTIVE_ATTEMPTS).lt(2));
        if (mongoTemplate.updateFirst(underLimit, new Update().inc(CAMPO_ADAPTIVE_ATTEMPTS, 1), COLLECTION)
                .getModifiedCount() == 1) {
            return 0;
        }
        Document ipState = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ipId)),
                Document.class, COLLECTION);
        if (ipState == null || ipState.getDate(CAMPO_WINDOW_START) == null) {
            return WINDOW_SECONDS;
        }
        return segundosRestantes(ipState.getDate(CAMPO_WINDOW_START).toInstant().plusSeconds(WINDOW_SECONDS), now);
    }

    private int esperaBloqueoCuenta(String accountId, Instant now) {
        Document account = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(accountId)),
                Document.class, COLLECTION);
        if (account == null || account.getDate(CAMPO_LOCK_UNTIL) == null) {
            return 0;
        }
        return segundosRestantes(account.getDate(CAMPO_LOCK_UNTIL).toInstant(), now);
    }

    private void ensureTtlIndex() {
        if (ttlIndexReady.compareAndSet(false, true)) {
            try {
                mongoTemplate.indexOps(COLLECTION)
                        .createIndex(new Index().on(CAMPO_EXPIRES_AT, org.springframework.data.domain.Sort.Direction.ASC)
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
                    .setOnInsert(CAMPO_WINDOW_START, Date.from(now))
                    .setOnInsert(CAMPO_ATTEMPTS, 0)
                    .setOnInsert(CAMPO_ADAPTIVE_ATTEMPTS, 0)
                    .setOnInsert(CAMPO_FAILURES, 0)
                    .setOnInsert(CAMPO_BACKOFF_STEP, 0)
                    .setOnInsert(CAMPO_USERS, new ArrayList<String>())
                    .setOnInsert(CAMPO_EXPIRES_AT, expiry), COLLECTION);
        } catch (DuplicateKeyException ignored) {
            // Another request created the window; subsequent atomic updates decide the winner.
        }
        Query expired = Query.query(Criteria.where("_id").is(documentId)
                .and(CAMPO_WINDOW_START).lte(Date.from(now.minusSeconds(windowSeconds))));
        Update reset = new Update().set(CAMPO_WINDOW_START, Date.from(now)).set(CAMPO_EXPIRES_AT, expiry);
        if (preserveBackoff) {
            reset.set(CAMPO_FAILURES, 0);
        } else {
                reset.set(CAMPO_ATTEMPTS, 0).set(CAMPO_ADAPTIVE_ATTEMPTS, 0).set(CAMPO_USERS, List.of())
                    .unset(CAMPO_NEXT_ALLOWED_AT);
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