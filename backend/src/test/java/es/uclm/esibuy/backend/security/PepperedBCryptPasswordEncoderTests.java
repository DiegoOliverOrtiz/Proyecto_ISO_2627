package es.uclm.esibuy.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PepperedBCryptPasswordEncoderTests {
    private static final String PEPPER = "test-pepper-that-is-never-used-outside-tests-123456";

    @Test
    void hashesWithPepperAndVerifiesOnlyWithTheSamePepper() {
        PepperedBCryptPasswordEncoder encoder = new PepperedBCryptPasswordEncoder(PEPPER, 4);
        String encoded = encoder.encode("StrongTestPassword9!");

        assertTrue(encoded.startsWith("{bcrypt-pepper-v1}"));
        assertTrue(encoder.matches("StrongTestPassword9!", encoded));
        assertFalse(new PepperedBCryptPasswordEncoder("different-test-pepper-that-is-long-enough-123", 4)
                .matches("StrongTestPassword9!", encoded));
    }

    @Test
    void verifiesLegacyBcryptHashesAndMarksThemForUpgrade() {
        BCryptPasswordEncoder legacyEncoder = new BCryptPasswordEncoder(4);
        String legacyHash = legacyEncoder.encode("LegacyPassword9!");
        PepperedBCryptPasswordEncoder encoder = new PepperedBCryptPasswordEncoder(PEPPER, 4);

        assertTrue(encoder.matches("LegacyPassword9!", legacyHash));
        assertTrue(encoder.upgradeEncoding(legacyHash));
    }

    @Test
    void requiresAnExternalPepper() {
        assertThrows(IllegalArgumentException.class, () -> new PepperedBCryptPasswordEncoder("", 12));
    }
}