package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;

import es.uclm.esibuy.backend.model.ProductoImagenDocumento;

class ProductoImagenMongoServiceTests {

    private final MongoTemplate plantilla =
            mock(MongoTemplate.class);

    private final ProductoImagenMongoService mongo =
            new ProductoImagenMongoService(plantilla);

    private final ProductoImagenService servicio =
            new ProductoImagenService(
                    "uploads/productos", "mongo", mongo);

    private static final byte[] GIF = {
            'G', 'I', 'F', '8', '9', 'a',
            1, 0, 1, 0, 0x3B
    };

    @Test
    void almacenaImagenEnMongoYConservaRutaPublica() {

        var ruta = servicio.guardar(
                new MockMultipartFile(
                        "imagen",
                        "foto.gif",
                        "image/gif",
                        GIF));

        assertTrue(ruta.startsWith("/product-images/"));
        assertTrue(ruta.endsWith(".gif"));

        var captor = ArgumentCaptor.forClass(
                ProductoImagenDocumento.class);

        verify(plantilla).insert(captor.capture());

        assertEquals(
                "image/gif",
                captor.getValue().getContentType());

        assertArrayEquals(
                GIF,
                captor.getValue().getContenido());

        assertEquals(
                ruta.substring("/product-images/".length()),
                captor.getValue().getId());
    }

    @Test
    void eliminaImagenEnMongo() {

        var ruta = servicio.guardar(
                new MockMultipartFile(
                        "imagen",
                        "foto.gif",
                        "image/gif",
                        GIF));

        servicio.eliminar(ruta);

        verify(plantilla).remove(
                any(Query.class),
                eq(ProductoImagenDocumento.class));
    }

    @Test
    void rutaInvalidaNoConsultaMongo() {

        assertTrue(
                mongo.obtener("../secretos.txt").isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> mongo.eliminar("../secretos.txt"));

        verifyNoInteractions(plantilla);
    }
}
