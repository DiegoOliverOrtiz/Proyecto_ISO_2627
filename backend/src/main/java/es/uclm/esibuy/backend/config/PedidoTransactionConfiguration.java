package es.uclm.esibuy.backend.config;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class PedidoTransactionConfiguration {
    @Bean
    public MongoTransactionManager pedidoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build()) {
            @Override
            protected void doCommit(MongoTransactionObject transaction) throws Exception {
                for (int intento = 0; ; intento++) {
                    try {
                        super.doCommit(transaction);
                        return;
                    } catch (MongoException exception) {
                        if (intento >= 2 || !exception.hasErrorLabel(
                                MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                            throw exception;
                        }
                    }
                }
            }
        };
    }
}
