package es.uclm.esibuy.backend.services;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "PERSONAL_DATA_MIGRATE", havingValue = "true")
public class DatosPersonalesMigration implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatosPersonalesMigration.class);
    private static final String COLECCION_USUARIOS = "usuarios";
    private static final List<String> CAMPOS = List.of("dniNif", "telefono");

    private final MongoTemplate mongoTemplate;
    private final DatosPersonalesCryptoService cifrado;

    public DatosPersonalesMigration(
            MongoTemplate mongoTemplate,
            DatosPersonalesCryptoService cifrado) {
        this.mongoTemplate = mongoTemplate;
        this.cifrado = cifrado;
    }

    @Override
    public void run(ApplicationArguments args) {
        int camposMigrados = 0;
        Criteria[] camposLegados = CAMPOS.stream()
                .map(campo -> Criteria.where(campo).exists(true).not().regex("^"
                        + DatosPersonalesCryptoService.PREFIJO_CIFRADO))
                .toArray(Criteria[]::new);

        try (var documentos = mongoTemplate.stream(
                Query.query(new Criteria().orOperator(camposLegados)),
                Document.class,
                COLECCION_USUARIOS)) {
            var iterador = documentos.iterator();
            while (iterador.hasNext()) {
                camposMigrados += migrarDocumento(iterador.next());
            }
        }

        LOGGER.info("Migración de cifrado de datos personales completada: {} campos actualizados.",
                camposMigrados);
    }

    private int migrarDocumento(Document documento) {
        int camposMigrados = 0;
        Object id = documento.get("_id");
        for (String campo : CAMPOS) {
            Object valorAlmacenado = documento.get(campo);
            if (valorAlmacenado instanceof String valor && !cifrado.estaCifrado(valor)) {
                var resultado = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(id).and(campo).is(valor)),
                        new Update().set(campo, cifrado.cifrar(valor, campo)),
                        COLECCION_USUARIOS);
                camposMigrados += (int) resultado.getModifiedCount();
            }
        }
        return camposMigrados;
    }
}
