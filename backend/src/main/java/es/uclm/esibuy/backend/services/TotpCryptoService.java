package es.uclm.esibuy.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 32;

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpCryptoService(@Value("${TOTP_ENCRYPTION_KEY:}") String encryptionKey) {
        this.key = decodificarClave(encryptionKey);
        try {
            byte[] legacyFingerprint = HexFormat.of().parseHex(
                    "2fb9ec72271e386a7eda2d77126a92e952127b6c6bcbd8992576690c1c0d75c3");
            if (MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(key), legacyFingerprint)) {
                throw new IllegalStateException("La clave TOTP de desarrollo debe sustituirse por una clave privada nueva.");
            }
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] decodificarClave(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY debe estar configurada.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptionKey);
            if (decoded.length != KEY_LENGTH) {
                throw new IllegalArgumentException();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY debe ser una clave Base64 de 32 bytes.", exception);
        }
    }

    public String cifrar(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP.", exception);
        }
    }

    public String descifrar(String value) {
        return descifrar(value, key);
    }

    static String descifrarConClaveAnterior(String value, String previousKey) {
        return descifrar(value, decodificarClave(previousKey));
    }

    private static String descifrar(String value, byte[] key) {
        try {
            String[] parts = value.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo descifrar el secreto TOTP.", exception);
        }
    }
}
