package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

class ProductoImagenServiceTests {
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
    private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, (byte) 0xFF, (byte) 0xD9 };
    private static final byte[] GIF = { 'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0x3B };
    private static final String MENSAJE = "La imagen debe ser PNG, JPG o GIF y no superar 5 MB.";

    @TempDir
    Path directorioTemporal;

    private ProductoImagenService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ProductoImagenService(directorioTemporal.toString());
    }

    private static MockMultipartFile imagen(String nombre, String contentType, byte[] contenido) {
        return new MockMultipartFile("imagen", nombre, contentType, contenido);
    }

    private static byte[] texto(String contenido) {
        return contenido.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatenar(byte[] base, String extra) {
        byte[] adicional = texto(extra);
        byte[] resultado = new byte[base.length + adicional.length];
        System.arraycopy(base, 0, resultado, 0, base.length);
        System.arraycopy(adicional, 0, resultado, base.length, adicional.length);
        return resultado;
    }

    // Una imagen del tamaño indicado que empieza y acaba como un PNG válido.
    private static byte[] pngDeTamano(int tamano) {
        byte[] contenido = new byte[tamano];
        System.arraycopy(PNG, 0, contenido, 0, PNG.length);
        System.arraycopy(PNG, 0, contenido, tamano - PNG.length, PNG.length);
        return contenido;
    }

    private void assertRechazada(MockMultipartFile imagen) throws IOException {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> servicio.guardar(imagen));
        assertEquals(MENSAJE, error.getMessage());
        try (Stream<Path> archivos = Files.list(directorioTemporal)) {
            assertEquals(0, archivos.count(), "No debe guardarse ningún archivo");
        }
    }

    static Stream<Arguments> imagenesValidas() {
        return Stream.of(
                Arguments.of("foto.png", "image/png", PNG, ".png"),
                Arguments.of("foto.jpg", "image/jpeg", JPG, ".jpg"),
                Arguments.of("foto.JPEG", "image/jpeg", JPG, ".jpg"),
                Arguments.of("foto.gif", "image/gif", GIF, ".gif"));
    }

    @ParameterizedTest
    @MethodSource("imagenesValidas")
    void guardaYEliminaImagenValidaConNombreSeguro(String nombre, String contentType, byte[] contenido, String extension) {
        String ruta = servicio.guardar(imagen(nombre, contentType, contenido));
        String guardado = Path.of(ruta).getFileName().toString();
        Path archivo = directorioTemporal.resolve(guardado);

        assertTrue(ruta.startsWith("/product-images/"));
        assertTrue(guardado.endsWith(extension));
        assertFalse(guardado.toLowerCase().contains("foto"), "El nombre original no se reutiliza");
        assertTrue(Files.exists(archivo));
        servicio.eliminar(ruta);
        assertFalse(Files.exists(archivo));
    }

    @Test
    void sinImagenDevuelveNull() {
        assertNull(servicio.guardar(null));
        assertNull(servicio.guardar(imagen("vacia.png", "image/png", new byte[0])));
    }

    @Test
    void rechazaScriptRenombradoComoPng() throws IOException {
        assertRechazada(imagen("script.png", "image/png", texto("<?php system($_GET[0]); ?>")));
        assertRechazada(imagen("script.png", "image/png", texto("<script>alert(1)</script>")));
    }

    @Test
    void rechazaExtensionOContentTypeQueNoCoincidenConElTipoReal() throws IOException {
        assertRechazada(imagen("foto.jpg", "image/png", PNG));
        assertRechazada(imagen("foto.png", "image/jpeg", PNG));
        assertRechazada(imagen("foto.php", "image/png", PNG));
        assertRechazada(imagen("foto", "image/png", PNG));
        assertRechazada(imagen("foto.png", "text/html", PNG));
        assertRechazada(imagen("foto.png", null, PNG));
    }

    @Test
    void rechazaDobleExtensionYNombresConRuta() throws IOException {
        assertRechazada(imagen("foto.png.php", "image/png", PNG));
        assertRechazada(imagen("../foto.png", "image/png", PNG));
        assertRechazada(imagen("..\\foto.png", "image/png", PNG));
        assertRechazada(imagen("foto\0.png", "image/png", PNG));
        assertRechazada(imagen(null, "image/png", PNG));
    }

    @Test
    void rechazaArchivosPoliglotasConDatosTrasLaImagen() throws IOException {
        assertRechazada(imagen("poli.png", "image/png", concatenar(PNG, "<?php echo 1; ?>")));
        assertRechazada(imagen("poli.jpg", "image/jpeg", concatenar(JPG, "<script>alert(1)</script>")));
        assertRechazada(imagen("poli.gif", "image/gif", concatenar(GIF, "<?php echo 1; ?>")));
    }

    @Test
    void rechazaImagenesTruncadas() throws IOException {
        assertRechazada(imagen("corta.png", "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }));
        assertRechazada(imagen("corta.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8 }));
    }

    @Test
    void rechazaImagenMayorDeCincoMegabytes() throws IOException {
        assertRechazada(imagen("grande.png", "image/png", pngDeTamano(5 * 1024 * 1024 + 1)));
    }

    @Test
    void aceptaImagenDeExactamenteCincoMegabytes() {
        assertNotNull(servicio.guardar(imagen("limite.png", "image/png", pngDeTamano(5 * 1024 * 1024))));
    }

    @Test
    void rechazaTiposNoPermitidos() throws IOException {
        assertRechazada(imagen("archivo.svg", "image/svg+xml", texto("<svg/>")));
        assertRechazada(imagen("doc.pdf", "application/pdf", texto("%PDF-1.4")));
        assertRechazada(imagen("pagina.html", "text/html", texto("<html></html>")));
        assertRechazada(imagen("programa.exe", "application/octet-stream", new byte[] { 'M', 'Z', 0, 0 }));
        assertRechazada(imagen("foto.webp", "image/webp", new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' }));
    }

    @Test
    void eliminarNoSaleDelDirectorioDeImagenes() throws IOException {
        Path fuera = Files.createTempFile("fuera", ".png");
        try {
            String ruta = "/product-images/../" + fuera.getFileName();
            assertThrows(IllegalArgumentException.class, () -> servicio.eliminar(ruta));
            assertTrue(Files.exists(fuera));
        } finally {
            Files.deleteIfExists(fuera);
        }
    }
}
