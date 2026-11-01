package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import es.uclm.esibuy.backend.dao.TotpSecurityStore;
import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.TooManyTwoFactorAttemptsException;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;

class TotpServiceTests {
    // Vector público de pruebas TOTP; no pertenece a ninguna cuenta real.
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // gitleaks:allow
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private TotpSecurityStore store;
    private MongoTemplate mongo;
    private TotpCryptoService crypto;
    private TotpService service;
    private Cliente user;

    @BeforeEach
    void preparar() {
        store = mock(TotpSecurityStore.class);
        mongo = mock(MongoTemplate.class);
        crypto = new TotpCryptoService(Base64.getEncoder().encodeToString(new byte[32]));
        service = new TotpService(mock(UsuarioDAO.class), crypto, encoder, store, mongo);
        user = new Cliente();
        user.setId("usuario-1");
        user.setActivada(true);
        user.setTotpActivado(true);
        user.setSecretoTotpCifrado(crypto.cifrar(SECRET));
        when(store.reservarIntento(eq(user.getId()), any(Instant.class))).thenReturn(true);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Usuario.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    private String codigoActual() {
        return service.generarCodigo(SECRET, Instant.now().getEpochSecond() / 30);
    }

    @Test
    void calculaLosVectoresOficialesYConservaElCeroInicial() {
        assertEquals("287082", service.generarCodigo(SECRET, 1));
        assertEquals("081804", service.generarCodigo(SECRET, 37037036));
    }

    @Test
    void validaUnCodigoGeneradoConUnSecretoNuevo() {
        String secret = service.generarSecreto();
        String code = service.generarCodigo(secret, Instant.now().getEpochSecond() / 30);
        assertTrue(service.verificar(secret, code));
        assertFalse(service.verificar(secret, "invalido"));
    }

    private static Stream<String> secretosQr() {
        var datosPrueba = new Random(0);
        return Stream.concat(Stream.of(SECRET), Stream.generate(() -> {
            var secreto = new StringBuilder(32);
            for (int i = 0; i < 32; i++) {
                secreto.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".charAt(datosPrueba.nextInt(32)));
            }
            return secreto.toString();
        }).limit(64));
    }

    @ParameterizedTest(name = "QR {index}")
    @MethodSource("secretosQr")
    void elQrContieneLaUriDeAltaCompleta(String secreto) throws Exception {
        String uri = service.crearUriAlta("cliente@example.com", secreto);
        String dataUrl = service.generarQrDataUrl(uri);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder()
                .decode(dataUrl.substring("data:image/png;base64,".length()))));
        // El PNG es un QR monocromo y sin perspectiva, generado directamente por el servicio.
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image))), Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
        assertEquals(uri, decoded);
    }

    @Test
    void generaOchoCodigosDeRespaldoDeOchentaBits() {
        var codes = service.generarCodigosRespaldo();
        assertEquals(8, codes.size());
        assertEquals(8, codes.stream().distinct().count());
        assertTrue(codes.stream().allMatch(code -> code.matches("[A-Z2-7]{16}")));
    }

    @Test
    void noAceptaElMismoIntervaloTotpEnDosVerificaciones() {
        when(store.consumirIntervalo(eq(user.getId()), eq(SECRET), anyLong())).thenReturn(true, false);
        String code = codigoActual();
        assertTrue(service.verificarSegundoFactor(user, code));
        assertFalse(service.verificarSegundoFactor(user, code));
    }

    @Test
    void elLimiteSeCompruebaAntesDeValidarElCodigo() {
        when(store.reservarIntento(eq(user.getId()), any(Instant.class))).thenReturn(false);
        assertThrows(TooManyTwoFactorAttemptsException.class,
                () -> service.verificarSegundoFactor(user, codigoActual()));
        verify(store, never()).consumirIntervalo(anyString(), anyString(), anyLong());
        verifyNoInteractions(mongo);
    }

    @Test
    void unCodigoConsumidoNoSeRestauraAlCargarUnaCopiaAntiguaDelUsuario() {
        String code = "ABCDEFGH234567AB";
        String hash = encoder.encode(code);
        user.setCodigosRespaldoTotp(List.of(hash));
        when(store.consumirRespaldo(user.getId(), hash)).thenReturn(true, false);
        assertTrue(service.verificarSegundoFactor(user, code));
        user.setCodigosRespaldoTotp(List.of(hash));
        assertFalse(service.verificarSegundoFactor(user, code));
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Usuario.class));
    }

    @Test
    void conservaLaCompatibilidadConLosCodigosAntiguos() {
        String code = "ABCDEFG2";
        String hash = encoder.encode(code);
        user.setCodigosRespaldoTotp(List.of(hash));
        when(store.consumirRespaldo(user.getId(), hash)).thenReturn(true);
        assertTrue(service.verificarSegundoFactor(user, "abcd-efg2"));
    }

    @Test
    void desactivarExigeUnCodigoCorrectoYNoReutilizado() {
        assertThrows(IllegalArgumentException.class, () -> service.desactivar(user, "incorrecto"));
        assertTrue(user.isTotpActivado());
        verifyNoInteractions(mongo);
        when(store.consumirIntervalo(eq(user.getId()), eq(SECRET), anyLong())).thenReturn(true);
        service.desactivar(user, codigoActual());
        assertFalse(user.isTotpActivado());
        assertNull(user.getSecretoTotpCifrado());
        assertTrue(user.getCodigosRespaldoTotp().isEmpty());
    }

    @Test
    void rechazaLaConfiguracionCaducadaSinActivarElSegundoFactor() {
        user.setTotpActivado(false);
        user.setSecretoTotpPendienteCifrado(crypto.cifrar(SECRET));
        user.setCaducidadTotpPendiente(Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.confirmarConfiguracion(user, codigoActual()));
        assertFalse(user.isTotpActivado());
        verifyNoInteractions(mongo);
    }

    @Test
    void alActivarGuardaHashesYConsumeElCodigoDeConfirmacion() {
        user.setTotpActivado(false);
        user.setSecretoTotpCifrado(null);
        user.setSecretoTotpPendienteCifrado(crypto.cifrar(SECRET));
        user.setCaducidadTotpPendiente(Instant.now().plusSeconds(600));
        when(store.consumirIntervalo(eq(user.getId()), eq(SECRET), anyLong())).thenReturn(true, false);
        String code = codigoActual();
        var response = service.confirmarConfiguracion(user, code);
        assertTrue(user.isTotpActivado());
        assertEquals(8, response.backupCodes().size());
        for (int i = 0; i < 8; i++) {
            assertTrue(encoder.matches(response.backupCodes().get(i), user.getCodigosRespaldoTotp().get(i)));
        }
        assertFalse(service.verificarSegundoFactor(user, code));
    }

    @Test
    void rechazaLaCuentaBloqueadaODesactivadaInclusoConUnTotpValido() {
        user.setBloqueada(true);
        assertFalse(service.verificarSegundoFactor(user, codigoActual()));
        user.setBloqueada(false);
        user.setActivada(false);
        assertFalse(service.verificarSegundoFactor(user, codigoActual()));
        verify(store, never()).consumirIntervalo(anyString(), anyString(), anyLong());
    }
}
