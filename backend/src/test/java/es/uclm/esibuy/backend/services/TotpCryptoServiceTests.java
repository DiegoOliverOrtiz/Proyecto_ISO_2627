package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TotpCryptoServiceTests {
    private final TotpCryptoService crypto = new TotpCryptoService(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void exigeUnaClaveExplicitaDeTreintaYDosBytes() {
        assertThrows(IllegalStateException.class, () -> new TotpCryptoService(null));
        assertThrows(IllegalStateException.class, () -> new TotpCryptoService(""));
        assertThrows(IllegalStateException.class, () -> new TotpCryptoService("invalida"));
        assertThrows(IllegalStateException.class, () -> new TotpCryptoService(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void rechazaLaAntiguaClavePublicaDeDesarrollo() {
        assertThrows(IllegalStateException.class,
                () -> new TotpCryptoService("VGhpc0lzQURldm9ubHlLZXlGb3JUb3RwMTIzNDU2Nzg="));
    }

    @Test
    void cifraConUnIvNuevoEnCadaOperacion() {
        String first = crypto.cifrar("SECRET");
        String second = crypto.cifrar("SECRET");
        assertNotEquals(first, second);
        assertEquals("SECRET", crypto.descifrar(first));
        assertEquals("SECRET", crypto.descifrar(second));
    }

    @Test
    void rechazaUnCifradoAlteradoOUnaClaveDiferente() {
        String encrypted = crypto.cifrar("SECRET");
        String[] parts = encrypted.split(":", 2);
        byte[] bytes = Base64.getDecoder().decode(parts[1]);
        bytes[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> crypto.descifrar(parts[0] + ":" + Base64.getEncoder().encodeToString(bytes)));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        TotpCryptoService other = new TotpCryptoService(Base64.getEncoder().encodeToString(otherKey));
        assertThrows(IllegalStateException.class, () -> other.descifrar(encrypted));
    }
}
