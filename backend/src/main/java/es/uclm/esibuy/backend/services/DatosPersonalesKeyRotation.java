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

@Component
@ConditionalOnProperty(name = "PERSONAL_DATA_ROTATE", havingValue = "true")
public class DatosPersonalesKeyRotation implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatosPersonalesKeyRotation.class);
    private static final String COLECCION_USUARIOS = "usuarios";
    private static final List<String> CAMPOS = List.of("dniNif", "telefono");

    private final MongoTemplate mongoTemplate;
    private final DatosPersonalesCryptoService cifrado;

    public DatosPersonalesKeyRotation(
            MongoTemplate mongoTemplate,
            DatosPersonalesCryptoService cifrado,
            @Value("${PERSONAL_DATA_MIGRATE:false}") boolean migracionInicialActiva) {
        if (migracionInicialActiva) {
            throw new IllegalStateException(
                    "PERSONAL_DATA_MIGRATE y PERSONAL_DATA_ROTATE no pueden activarse a la vez.");
        }
        if (!cifrado.tieneClaveAnterior()) {
            throw new IllegalStateException(
                    "La rotación requiere PERSONAL_DATA_PREVIOUS_ENCRYPTION_KEY.");
        }
        if (!cifrado.tieneClavesDistintas()) {
            throw new IllegalStateException(
                    "La clave anterior y la actual deben ser distintas para rotar.");
        }
        this.mongoTemplate = mongoTemplate;
        this.cifrado = cifrado;
    }

    @Override
    public void run(ApplicationArguments args) {
        int camposRotados = 0;
        Criteria[] camposCifrados = CAMPOS.stream()
                .map(campo -> Criteria.where(campo)
                        .regex("^" + DatosPersonalesCryptoService.PREFIJO_CIFRADO))
                .toArray(Criteria[]::new);

        try (var documentos = mongoTemplate.stream(
                Query.query(new Criteria().orOperator(camposCifrados)),
                Document.class,
                COLECCION_USUARIOS)) {
            var iterador = documentos.iterator();
            while (iterador.hasNext()) {
                camposRotados += rotarDocumento(iterador.next());
            }
        }

        LOGGER.info("Rotación de clave de datos personales completada: {} campos actualizados.",
                camposRotados);
    }

    private int rotarDocumento(Document documento) {
        int camposRotados = 0;
        Object id = documento.get("_id");
        for (String campo : CAMPOS) {
            Object valorAlmacenado = documento.get(campo);
            if (valorAlmacenado instanceof String valor && cifrado.estaCifrado(valor)) {
                String valorRotado = cifrado.rotar(valor, campo);
                if (!valor.equals(valorRotado)) {
                    var resultado = mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(id).and(campo).is(valor)),
                            new Update().set(campo, valorRotado),
                            COLECCION_USUARIOS);
                    camposRotados += (int) resultado.getModifiedCount();
                }
            }
        }
        return camposRotados;
    }
}
