package es.uclm.esibuy.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PepperedBCryptPasswordEncoder implements PasswordEncoder {
    private static final String PREFIX = "{bcrypt-pepper-v1}";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String pepper;
    private final BCryptPasswordEncoder bcrypt;

    public PepperedBCryptPasswordEncoder(String pepper, int strength) {
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalArgumentException("APP_SECURITY_PASSWORD_PEPPER debe tener al menos 32 caracteres.");
        }
        this.pepper = pepper;
        this.bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + bcrypt.encode(pepperedMaterial(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith(PREFIX)) {
            return bcrypt.matches(pepperedMaterial(rawPassword), encodedPassword.substring(PREFIX.length()));
        }
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.startsWith(PREFIX)) {
            return true;
        }
        return bcrypt.upgradeEncoding(encodedPassword.substring(PREFIX.length()));
    }

    private String pepperedMaterial(CharSequence rawPassword) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo aplicar el pepper a la contraseña.", exception);
        }
    }
}