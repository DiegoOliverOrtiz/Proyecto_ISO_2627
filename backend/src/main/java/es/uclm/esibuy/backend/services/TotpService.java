package es.uclm.esibuy.backend.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Date;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.dao.TotpSecurityStore;
import es.uclm.esibuy.backend.dto.TwoFactorConfirmationResponse;
import es.uclm.esibuy.backend.exceptions.TooManyTwoFactorAttemptsException;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BACKUP_CODE_COUNT = 8;
    private static final int STEP_SECONDS = 30;

    private final UsuarioDAO usuarioDAO;
    private final TotpCryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;
    private final TotpSecurityStore securityStore;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(UsuarioDAO usuarioDAO, TotpCryptoService cryptoService, PasswordEncoder passwordEncoder,
            TotpSecurityStore securityStore, MongoTemplate mongoTemplate) {
        this.usuarioDAO = usuarioDAO;
        this.cryptoService = cryptoService;
        this.passwordEncoder = passwordEncoder;
        this.securityStore = securityStore;
        this.mongoTemplate = mongoTemplate;
    }

    public String generarSecreto() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return codificarBase32(bytes);
    }

    public String crearUriAlta(String email, String secret) {
        return "otpauth://totp/ESIBuy:" + encode(email)
                + "?secret=" + secret
                + "&issuer=ESIBuy&algorithm=SHA1&digits=6&period=30";
    }

    public String generarQrDataUrl(String otpauthUri) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    otpauthUri,
                    BarcodeFormat.QR_CODE,
                    280,
                    280,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            var image = new java.awt.image.BufferedImage(280, 280, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 280; x++) {
                for (int y = 0; y < 280; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo generar el QR de TOTP.", exception);
        }
    }

    public boolean verificar(String secret, String code) {
        return intervaloValido(secret, code) != null;
    }

    private Long intervaloValido(String secret, String code) {
        String normalizedCode = normalizarCodigo(code);
        if (secret == null || normalizedCode == null || !normalizedCode.matches("\\d{6}")) {
            return null;
        }
        long currentStep = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(generarCodigo(secret, currentStep + offset), normalizedCode)) {
                return currentStep + offset;
            }
        }
        return null;
    }

    public boolean verificarSegundoFactor(Usuario usuario, String code) {
        reservarIntento(usuario);
        if (!usuario.isActivada() || usuario.isBloqueada() || !usuario.isTotpActivado()
                || usuario.getSecretoTotpCifrado() == null) {
            return false;
        }
        String secret = descifrar(usuario.getSecretoTotpCifrado());
        Long step = intervaloValido(secret, code);
        if (step != null) {
            return securityStore.consumirIntervalo(usuario.getId(), secret, step);
        }
        return consumirCodigoRespaldo(usuario, code);
    }

    private void reservarIntento(Usuario usuario) {
        if (!securityStore.reservarIntento(usuario.getId(), Instant.now())) {
            throw new TooManyTwoFactorAttemptsException();
        }
    }

    public boolean consumirCodigoRespaldo(Usuario usuario, String code) {
        String normalizedCode = normalizarCodigo(code);
        if (normalizedCode == null || !normalizedCode.toUpperCase(Locale.ROOT).matches("[A-Z2-7]{8}|[A-Z2-7]{16}")) {
            return false;
        }
        List<String> remaining = new ArrayList<>(usuario.getCodigosRespaldoTotp());
        for (int i = 0; i < remaining.size(); i++) {
            if (passwordEncoder.matches(normalizedCode.toUpperCase(Locale.ROOT), remaining.get(i))) {
                String hash = remaining.get(i);
                if (!securityStore.consumirRespaldo(usuario.getId(), hash)) {
                    return false;
                }
                if (mongoTemplate.updateFirst(Query.query(cuentaActiva(usuario)
                                .and("totpActivado").is(true)
                                .and("secretoTotpCifrado").is(usuario.getSecretoTotpCifrado())
                                .and("codigosRespaldoTotp").is(hash)),
                        new Update().pull("codigosRespaldoTotp", hash), Usuario.class).getModifiedCount() != 1) {
                    return false;
                }
                remaining.remove(i);
                usuario.setCodigosRespaldoTotp(remaining);
                return true;
            }
        }
        return false;
    }

    public String cifrar(String secret) {
        return cryptoService.cifrar(secret);
    }

    public String descifrar(String encryptedSecret) {
        return cryptoService.descifrar(encryptedSecret);
    }

    public Usuario buscarUsuario(String email) {
        return usuarioDAO.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }

    public TwoFactorConfirmationResponse confirmarConfiguracion(Usuario usuario, String code) {
        reservarIntento(usuario);
        String pending = usuario.getSecretoTotpPendienteCifrado();
        if (usuario.isTotpActivado() || pending == null || usuario.getCaducidadTotpPendiente() == null
                || !Instant.now().isBefore(usuario.getCaducidadTotpPendiente())) {
            throw new IllegalArgumentException("No hay una configuración TOTP pendiente. Genera un QR nuevo.");
        }
        String secret = descifrar(pending);
        Long step = intervaloValido(secret, code);
        if (step == null || !securityStore.consumirIntervalo(usuario.getId(), secret, step)) {
            throw new IllegalArgumentException("El código TOTP no es válido o ya se ha utilizado.");
        }
        var backupCodes = generarCodigosRespaldo();
        var hashes = backupCodes.stream().map(passwordEncoder::encode).toList();
        String encrypted = cryptoService.cifrar(secret);
        var update = new Update().set("secretoTotpCifrado", encrypted)
                .set("totpActivado", true).set("codigosRespaldoTotp", hashes)
                .unset("secretoTotpPendienteCifrado").unset("caducidadTotpPendiente");
        if (mongoTemplate.updateFirst(Query.query(cuentaActiva(usuario)
                        .and("totpActivado").ne(true).and("secretoTotpPendienteCifrado").is(pending)
                        .and("caducidadTotpPendiente").gt(Date.from(Instant.now()))),
                update, Usuario.class).getModifiedCount() != 1) {
            throw new IllegalArgumentException("La configuración ha cambiado. Genera un QR nuevo.");
        }
        usuario.setSecretoTotpCifrado(encrypted);
        usuario.setSecretoTotpPendienteCifrado(null);
        usuario.setCaducidadTotpPendiente(null);
        usuario.setCodigosRespaldoTotp(hashes);
        usuario.setTotpActivado(true);
        return new TwoFactorConfirmationResponse(true, backupCodes);
    }

    public void desactivar(Usuario usuario, String code) {
        if (!verificarSegundoFactor(usuario, code)) {
            throw new IllegalArgumentException("El código del segundo factor no es válido o ya se ha utilizado.");
        }
        if (mongoTemplate.updateFirst(Query.query(cuentaActiva(usuario)
                        .and("totpActivado").is(true)
                        .and("secretoTotpCifrado").is(usuario.getSecretoTotpCifrado())),
                new Update().set("totpActivado", false).unset("secretoTotpCifrado")
                        .unset("secretoTotpPendienteCifrado").unset("caducidadTotpPendiente")
                        .set("codigosRespaldoTotp", List.of()), Usuario.class).getModifiedCount() != 1) {
            throw new IllegalArgumentException("La configuración de seguridad ha cambiado. Recarga el perfil.");
        }
        usuario.setSecretoTotpCifrado(null);
        usuario.setSecretoTotpPendienteCifrado(null);
        usuario.setCodigosRespaldoTotp(List.of());
        usuario.setTotpActivado(false);
        usuario.setCaducidadTotpPendiente(null);
    }

    public List<String> generarCodigosRespaldo() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(generarCodigoRespaldo());
        }
        return codes;
    }

    public void guardarSecretoPendiente(Usuario usuario, String secret) {
        String encrypted = cryptoService.cifrar(secret);
        Instant expires = Instant.now().plusSeconds(600);
        if (mongoTemplate.updateFirst(Query.query(cuentaActiva(usuario).and("totpActivado").ne(true)),
                new Update().set("secretoTotpPendienteCifrado", encrypted)
                        .set("caducidadTotpPendiente", Date.from(expires)), Usuario.class).getModifiedCount() != 1) {
            throw new IllegalArgumentException("No se puede iniciar la configuración del segundo factor.");
        }
        usuario.setSecretoTotpPendienteCifrado(encrypted);
        usuario.setCaducidadTotpPendiente(expires);
    }

    public String obtenerSecretoPendiente(Usuario usuario) {
        String encrypted = usuario.getSecretoTotpPendienteCifrado();
        return encrypted == null ? null : cryptoService.descifrar(encrypted);
    }

    String generarCodigo(String base32Secret, long counter) {
        try {
            byte[] key = decodificarBase32(base32Secret);
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xff);
                counter >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo calcular el código TOTP.", exception);
        }
    }

    private String generarCodigoRespaldo() {
        byte[] bytes = new byte[10];
        secureRandom.nextBytes(bytes);
        return codificarBase32(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String normalizarCodigo(String code) {
        return code == null ? null : code.replaceAll("[\\s-]", "");
    }

    private Criteria cuentaActiva(Usuario usuario) {
        return Criteria.where("_id").is(usuario.getId()).and("activada").is(true).and("bloqueada").ne(true);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String codificarBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32.charAt((buffer >> bits) & 31));
                buffer = bits == 0 ? 0 : buffer & ((1 << bits) - 1);
            }
        }
        if (bits > 0) {
            result.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return result.toString();
    }

    private byte[] decodificarBase32(String value) {
        String normalized = value.replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream result = new ByteArrayOutputStream(normalized.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char character : normalized.toCharArray()) {
            int digit = BASE32.indexOf(character);
            if (digit < 0) {
                throw new IllegalArgumentException("Secreto Base32 no válido.");
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                result.write((buffer >> bits) & 0xff);
                buffer = bits == 0 ? 0 : buffer & ((1 << bits) - 1);
            }
        }
        return result.toByteArray();
    }
}
