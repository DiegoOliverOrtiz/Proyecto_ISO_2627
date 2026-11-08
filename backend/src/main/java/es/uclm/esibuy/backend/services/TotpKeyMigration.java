package es.uclm.esibuy.backend.services;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Migración explícita y repetible. La clave anterior nunca se usa para autenticar. */
@Component
@ConditionalOnProperty(name = "TOTP_MIGRATE_KEY", havingValue = "true")
public class TotpKeyMigration implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TotpKeyMigration.class);
    private final MongoTemplate mongoTemplate;
    private final TotpCryptoService cryptoService;
    private final String previousKey;

    public TotpKeyMigration(MongoTemplate mongoTemplate, TotpCryptoService cryptoService,
            @Value("${TOTP_PREVIOUS_ENCRYPTION_KEY:}") String previousKey) {
        if (previousKey == null || previousKey.isBlank()) {
            throw new IllegalStateException("La migración TOTP requiere TOTP_PREVIOUS_ENCRYPTION_KEY.");
        }
        this.mongoTemplate = mongoTemplate;
        this.cryptoService = cryptoService;
        this.previousKey = previousKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        int count = 0;
        for (String field : List.of("secretoTotpCifrado", "secretoTotpPendienteCifrado")) {
            try (var documents = mongoTemplate.stream(Query.query(Criteria.where(field).ne(null)),
                    Document.class, "usuarios")) {
                var iterator = documents.iterator();
                while (iterator.hasNext()) {
                    count += migrarSecreto(iterator.next(), field);
                }
            }
        }
        LOGGER.info("Migración de cifrado TOTP completada: {} secretos actualizados.", count);
    }

    private int migrarSecreto(Document document, String field) {
        String encrypted = document.getString(field);
        try {
            cryptoService.descifrar(encrypted);
            return 0; // Ya migrado: no vuelve a cifrar ni altera otros campos.
        } catch (IllegalStateException differentKey) {
            String plain = TotpCryptoService.descifrarConClaveAnterior(encrypted, previousKey);
            var result = mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(document.get("_id"))
                            .and(field).is(encrypted)),
                    new Update().set(field, cryptoService.cifrar(plain)), "usuarios");
            return (int) result.getModifiedCount();
        }
    }
}
