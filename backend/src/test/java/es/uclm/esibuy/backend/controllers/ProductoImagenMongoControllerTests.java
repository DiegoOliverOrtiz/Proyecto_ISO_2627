package es.uclm.esibuy.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import es.uclm.esibuy.backend.model.ProductoImagenDocumento;
import es.uclm.esibuy.backend.services.ProductoImagenMongoService;

class ProductoImagenMongoControllerTests {

    private final ProductoImagenMongoService mongo =
            mock(ProductoImagenMongoService.class);

    private final ProductoImagenMongoController controller =
            new ProductoImagenMongoController(mongo);

    @Test
    void devuelveImagenConTipoYProteccionNosniff() {

        String nombre =
                "d776a673-37be-49e6-a534-c9cad518284b.png";

        byte[] bytes = {1, 2, 3};

        when(mongo.obtener(nombre)).thenReturn(
                Optional.of(
                        new ProductoImagenDocumento(
                                nombre,
                                "image/png",
                                bytes)));

        var respuesta = controller.obtener(nombre);

        assertEquals(
                HttpStatus.OK,
                respuesta.getStatusCode());

        assertEquals(
                MediaType.IMAGE_PNG,
                respuesta.getHeaders().getContentType());

        assertEquals(
                "nosniff",
                respuesta.getHeaders().getFirst(
                        "X-Content-Type-Options"));

        assertArrayEquals(
                bytes,
                respuesta.getBody());
    }

    @Test
    void devuelve404SiNoExiste() {

        String nombre =
                "d776a673-37be-49e6-a534-c9cad518284b.png";

        when(mongo.obtener(nombre))
                .thenReturn(Optional.empty());

        assertEquals(
                HttpStatus.NOT_FOUND,
                controller.obtener(nombre).getStatusCode());
    }
}
