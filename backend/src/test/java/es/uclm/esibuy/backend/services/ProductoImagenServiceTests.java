package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ProductoImagenServiceTests {
    @TempDir
    Path directorioTemporal;

    @Test
    void guardaYEliminaImagenDeProducto() throws Exception {
        ProductoImagenService servicio = new ProductoImagenService(directorioTemporal.toString());
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen",
                "foto.png",
                "image/png",
                new byte[] { 1, 2, 3 });

        String ruta = servicio.guardar(imagen);
        Path archivo = directorioTemporal.resolve(Path.of(ruta).getFileName());

        assertNotNull(ruta);
        assertTrue(Files.exists(archivo));
        servicio.eliminar(ruta);
        assertFalse(Files.exists(archivo));
    }

    @Test
    void rechazaFormatosDeImagenNoPermitidos() {
        ProductoImagenService servicio = new ProductoImagenService(directorioTemporal.toString());
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen",
                "archivo.svg",
                "image/svg+xml",
                "<svg/>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> servicio.guardar(imagen));
    }
}
