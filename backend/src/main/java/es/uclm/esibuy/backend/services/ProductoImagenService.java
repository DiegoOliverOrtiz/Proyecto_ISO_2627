package es.uclm.esibuy.backend.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductoImagenService {
    private static final Map<String, String> EXTENSIONES_PERMITIDAS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif");
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final String RUTA_PUBLICA = "/product-images/";

    private final Path directorio;

    public ProductoImagenService(@Value("${app.productos.imagenes.directorio:uploads/productos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            return null;
        }

        String extension = EXTENSIONES_PERMITIDAS.get(imagen.getContentType());
        if (extension == null || imagen.getSize() > TAMANO_MAXIMO
                || !StringUtils.hasText(imagen.getOriginalFilename())) {
            throw new IllegalArgumentException("La imagen debe ser PNG, JPG o GIF y no superar 5 MB.");
        }

        String nombreArchivo = UUID.randomUUID() + extension;
        Path destino = directorio.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(directorio)) {
            throw new IllegalArgumentException("La ruta de la imagen no es válida.");
        }

        try {
            Files.createDirectories(directorio);
            imagen.transferTo(destino);
            return RUTA_PUBLICA + nombreArchivo;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen del producto.", e);
        }
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
