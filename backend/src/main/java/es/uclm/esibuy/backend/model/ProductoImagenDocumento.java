package es.uclm.esibuy.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "imagenes_producto")
public class ProductoImagenDocumento {

    @Id
    private String id;

    private String contentType;

    private byte[] contenido;

    public ProductoImagenDocumento() {
    }

    public ProductoImagenDocumento(
            String id,
            String contentType,
            byte[] contenido) {
        this.id = id;
        this.contentType = contentType;
        this.contenido = contenido;
    }

    public String getId() {
        return id;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContenido() {
        return contenido;
    }
}
