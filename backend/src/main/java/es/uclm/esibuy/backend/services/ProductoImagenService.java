package es.uclm.esibuy.backend.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductoImagenService {
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final String RUTA_PUBLICA = "/product-images/";
    private static final String MENSAJE_IMAGEN_NO_VALIDA = "La imagen debe ser PNG, JPG o GIF y no superar 5 MB.";

    // Lista blanca: tipo real detectado -> Content-Type declarado, extensiones de nombre admitidas y extensión final.
    private enum TipoImagen {
        PNG("image/png", ".png", "png"),
        JPG("image/jpeg", ".jpg", "jpg", "jpeg"),
        GIF("image/gif", ".gif", "gif");

        private final String contentType;
        private final String extensionGuardada;
        private final List<String> extensionesNombre;

        TipoImagen(String contentType, String extensionGuardada, String... extensionesNombre) {
            this.contentType = contentType;
            this.extensionGuardada = extensionGuardada;
            this.extensionesNombre = List.of(extensionesNombre);
        }
    }

    private final Path directorio;

    public ProductoImagenService(@Value("${app.productos.imagenes.directorio:uploads/productos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            return null;
        }

        if (imagen.getSize() > TAMANO_MAXIMO) {
            throw new IllegalArgumentException(MENSAJE_IMAGEN_NO_VALIDA);
        }

        byte[] contenido;
        try {
            contenido = imagen.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la imagen del producto.", e);
        }

        TipoImagen tipo = detectarTipoReal(contenido);
        // El tipo real, el Content-Type y la extensión del nombre deben coincidir; si no, el archivo es ambiguo.
        if (tipo == null
                || !tipo.contentType.equals(imagen.getContentType())
                || !tieneExtensionSegura(imagen.getOriginalFilename(), tipo)
                || !terminaCorrectamente(contenido, tipo)) {
            throw new IllegalArgumentException(MENSAJE_IMAGEN_NO_VALIDA);
        }

        // El nombre original nunca se usa: se genera uno propio con una extensión de la lista blanca.
        String nombreArchivo = UUID.randomUUID() + tipo.extensionGuardada;
        Path destino = directorio.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(directorio)) {
            throw new IllegalArgumentException("La ruta de la imagen no es válida.");
        }

        try {
            Files.createDirectories(directorio);
            Files.write(destino, contenido);
            return RUTA_PUBLICA + nombreArchivo;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen del producto.", e);
        }
    }

    // Firma (magic bytes) del archivo: no se confía en la extensión ni en el Content-Type.
    private static TipoImagen detectarTipoReal(byte[] c) {
        if (empiezaPor(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return TipoImagen.PNG;
        }
        if (empiezaPor(c, 0xFF, 0xD8, 0xFF)) {
            return TipoImagen.JPG;
        }
        if (empiezaPor(c, 'G', 'I', 'F', '8', '7', 'a') || empiezaPor(c, 'G', 'I', 'F', '8', '9', 'a')) {
            return TipoImagen.GIF;
        }
        return null;
    }

    // Rechaza políglotas: la imagen debe acabar con su marca de fin, sin datos añadidos detrás.
    private static boolean terminaCorrectamente(byte[] c, TipoImagen tipo) {
        return switch (tipo) {
            case PNG -> termina(c, 0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', 0xAE, 0x42, 0x60, 0x82);
            case JPG -> termina(c, 0xFF, 0xD9);
            case GIF -> termina(c, 0x3B);
        };
    }

    // Solo la última extensión cuenta, y el nombre no puede llevar rutas ni caracteres de control.
    private static boolean tieneExtensionSegura(String nombreOriginal, TipoImagen tipo) {
        if (!StringUtils.hasText(nombreOriginal)
                || nombreOriginal.chars().anyMatch(c -> c < 0x20 || c == '/' || c == '\\')) {
            return false;
        }
        int punto = nombreOriginal.lastIndexOf('.');
        return punto > 0 && tipo.extensionesNombre.contains(nombreOriginal.substring(punto + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean empiezaPor(byte[] contenido, int... esperado) {
        if (contenido.length < esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((contenido[i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean termina(byte[] contenido, int... esperado) {
        int inicio = contenido.length - esperado.length;
        if (inicio < 0) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((contenido[inicio + i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    public void eliminar(String rutaPublica) {
        if (rutaPublica == null || !rutaPublica.startsWith(RUTA_PUBLICA)) {
            return;
        }
        Path archivo = directorio.resolve(rutaPublica.substring(RUTA_PUBLICA.length())).normalize();
        if (!archivo.startsWith(directorio)) {
            throw new IllegalArgumentException("La ruta de la imagen no es válida.");
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar la imagen del producto.", e);
        }
    }
}
