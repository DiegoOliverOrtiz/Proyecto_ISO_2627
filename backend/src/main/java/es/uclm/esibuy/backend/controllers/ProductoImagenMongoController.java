package es.uclm.esibuy.backend.controllers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.services.ProductoImagenMongoService;

@RestController
@ConditionalOnProperty(
        name = "app.productos.imagenes.almacenamiento",
        havingValue = "mongo")
public class ProductoImagenMongoController {

    private final ProductoImagenMongoService imagenes;

    public ProductoImagenMongoController(
            ProductoImagenMongoService imagenes) {
        this.imagenes = imagenes;
    }

    @GetMapping("/product-images/{nombre:.+}")
    public ResponseEntity<byte[]> obtener(
            @PathVariable String nombre) {

        return imagenes.obtener(nombre)
                .map(imagen -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                imagen.getContentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.noCache())
                        .body(imagen.getContenido()))
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }
}
