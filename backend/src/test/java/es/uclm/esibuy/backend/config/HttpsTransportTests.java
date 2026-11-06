package es.uclm.esibuy.backend.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import es.uclm.esibuy.backend.dao.TipoMetodoPagoDAO;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.services.AuthService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "TOTP_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "TOTP_MIGRATE_KEY=false",
        "FRONTEND_ORIGIN=https://localhost:4200",
        "spring.mongodb.auto-index-creation=false",
        "server.ssl.enabled=true",
        "app.security.cookies.secure=true",
        "server.servlet.session.cookie.secure=true"
})
class HttpsTransportTests {
    private static Path keyStorePath;
    private static final String PASSWORD = UUID.randomUUID().toString();

    @MockitoBean
    private MongoClient mongoClient;
    @MockitoBean
    private TipoMetodoPagoDAO tipoMetodoPagoDAO;
    @MockitoBean
    private AuthService authService;
    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void certificateProperties(DynamicPropertyRegistry registry) throws Exception {
        Path directory = Files.createTempDirectory("esibuy-https-test-");
        keyStorePath = directory.resolve("localhost.p12");
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "keytool.exe"
                : "keytool";
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-genkeypair", "-alias", "esibuy", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", keyStorePath.toString(),
                "-storepass:env", "ESIBUY_TEST_TLS_PASSWORD", "-keypass:env", "ESIBUY_TEST_TLS_PASSWORD",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-ext", "EKU=serverAuth", "-validity", "1", "-noprompt");
        builder.environment().put("ESIBUY_TEST_TLS_PASSWORD", PASSWORD);
        var process = builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("No se pudo generar a tiempo el certificado de prueba.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("keytool no pudo generar el certificado de prueba.");
        }
        registry.add("server.ssl.key-store", () -> keyStorePath.toUri().toString());
        registry.add("server.ssl.key-store-password", () -> PASSWORD);
    }

    @AfterAll
    static void removeTestCertificate() throws IOException {
        if (keyStorePath != null) {
            Files.deleteIfExists(keyStorePath);
            Files.deleteIfExists(keyStorePath.getParent());
        }
    }

    private HttpClient trustedClient() throws Exception {
        KeyStore source = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStorePath)) {
            source.load(input, PASSWORD.toCharArray());
        }
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("localhost", source.getCertificate("esibuy"));
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(null, managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    @Test
    void laApiNegociaTlsValidaElCertificadoYPermiteElOrigenHttps() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/usuarios/csrf"))
                .header("Origin", "https://localhost:4200").GET().build();
        var response = trustedClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.sslSession().orElseThrow().getProtocol().matches("TLSv1\\.[23]"));
        assertEquals("https://localhost:4200",
                response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Strict-Transport-Security").isPresent());
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(
                value -> value.startsWith("XSRF-TOKEN=") && value.toLowerCase(Locale.ROOT).contains("; secure")));
    }

    @Test
    void elLoginPorHttpsEntregaUnaCookieDeSesionSecureYHttpOnly() throws Exception {
        Cliente user = new Cliente();
        user.setEmail("tls@example.com");
        user.setActivada(true);
        when(authService.autenticar(org.mockito.ArgumentMatchers.eq("tls@example.com"),
            org.mockito.ArgumentMatchers.eq("test-password"), org.mockito.ArgumentMatchers.anyString())).thenReturn(user);
        var client = trustedClient();
        var csrf = client.send(HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/usuarios/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        var token = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(csrf.body());
        assertTrue(token.find());
        var request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/usuarios/login"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", token.group(1))
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"email\":\"tls@example.com\",\"password\":\"test-password\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(
                value -> value.startsWith("JSESSIONID=") && value.toLowerCase(Locale.ROOT).contains("; secure")
                        && value.toLowerCase(Locale.ROOT).contains("; httponly")));
    }

    @Test
    void noAceptaPeticionesHttpSinCifrarEnElPuertoDeLaApi() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/usuarios/csrf"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertTrue(response.statusCode() >= 400);
            } catch (IOException rejected) {
                // Un conector TLS también puede cerrar la conexión antes de enviar una
                // respuesta HTTP.
            }
        }
    }
}
