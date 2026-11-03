package es.uclm.esibuy.backend.services;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.model.ProductoImagenDocumento;

@Service
public class ProductoImagenMongoService {

    private static final Pattern NOMBRE_VALIDO = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(png|jpg|gif)");

    private final MongoTemplate mongoTemplate;

    public ProductoImagenMongoService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void guardar(
            String nombre,
            String contentType,
            byte[] contenido) {

        validarNombre(nombre);

        mongoTemplate.insert(
                new ProductoImagenDocumento(
                        nombre,
                        contentType,
                        contenido));
    }

    public Optional<ProductoImagenDocumento> obtener(String nombre) {

        if (!esNombreValido(nombre)) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                mongoTemplate.findById(
                        nombre,
                        ProductoImagenDocumento.class));
    }

    public void eliminar(String nombre) {

        validarNombre(nombre);

        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(nombre)),
                ProductoImagenDocumento.class);
    }

    private static boolean esNombreValido(String nombre) {
        return nombre != null
                && NOMBRE_VALIDO.matcher(nombre).matches();
    }

    private static void validarNombre(String nombre) {
        if (!esNombreValido(nombre)) {
            throw new IllegalArgumentException(
                    "La ruta de la imagen no es válida.");
        }
    }
}
