package es.uclm.esibuy.backend.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProductoImagenConfiguration implements WebMvcConfigurer {
    private final Path directorio;

    public ProductoImagenConfiguration(
            @Value("${app.productos.imagenes.directorio:uploads/productos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations(directorio.toUri().toString());
    }
}
