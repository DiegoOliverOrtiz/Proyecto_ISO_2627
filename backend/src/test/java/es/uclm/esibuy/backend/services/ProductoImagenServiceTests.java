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
                new byte[] {
                    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                    0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82 });

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
