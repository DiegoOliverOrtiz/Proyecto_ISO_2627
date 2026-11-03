package es.uclm.esibuy.backend.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class ConfiguracionHttpsTests {

    private Properties configuracion() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    }

    @Test
    void laApiEscuchaEnElPuertoHttpsConTlsActivado() throws IOException {
        Properties props = configuracion();
        assertEquals("8443", props.getProperty("server.port"));
        assertEquals("true", props.getProperty("server.ssl.enabled"));
        assertEquals("PKCS12", props.getProperty("server.ssl.key-store-type"));
        assertEquals("TLSv1.2,TLSv1.3", props.getProperty("server.ssl.enabled-protocols"));
    }

    @Test
    void elKeystoreSeLeeDeVariablesDeEntornoConRutaPorDefecto() throws IOException {
        Properties props = configuracion();
        assertEquals("${SSL_KEY_STORE:file:../.certs/localhost.p12}", props.getProperty("server.ssl.key-store"));
        assertEquals("${SSL_KEY_STORE_PASSWORD:}", props.getProperty("server.ssl.key-store-password"));
    }

    @Test
    void lasCookiesDeSesionSonSecureYHttpOnly() throws IOException {
        Properties props = configuracion();
        assertEquals("true", props.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("true", props.getProperty("app.security.cookies.secure"));
        assertEquals("true", props.getProperty("server.servlet.session.cookie.http-only"));
    }

    @Test
    void laClaveTotpNoTieneValorPorDefectoEnElFichero() throws IOException {
        Properties props = configuracion();
        assertFalse(props.stringPropertyNames().stream().anyMatch(nombre -> nombre.contains("TOTP")));
    }

    @Test
    void importaElEnvDeLaRaizYDelBackend() throws IOException {
        String importacion = configuracion().getProperty("spring.config.import");
        assertTrue(importacion.contains("file:../.env"));
        assertTrue(importacion.contains("file:.env"));
    }
}
