package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatosPersonalesCryptoServiceTests {

    private static final String CLAVE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String CLAVE_NUEVA = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

    private final DatosPersonalesCryptoService cifrado = new DatosPersonalesCryptoService(CLAVE);

    @Test
    void cifraConIvAleatorioYRecuperaElValorOriginal() {
        String primero = cifrado.cifrar("12345678A", "dniNif");
        String segundo = cifrado.cifrar("12345678A", "dniNif");

        assertTrue(cifrado.estaCifrado(primero));
        assertNotEquals("12345678A", primero);
        assertNotEquals(primero, segundo);
        assertEquals("12345678A", cifrado.descifrar(primero, "dniNif"));
        assertThrows(IllegalStateException.class, () -> cifrado.descifrar(primero, "telefono"));
    }

    @Test
    void conservaNulosYPermiteLeerValoresLegadosDuranteLaMigracion() {
        assertEquals(null, cifrado.cifrar(null, "telefono"));
        assertEquals(null, cifrado.descifrar(null, "telefono"));
        assertEquals("600123123", cifrado.descifrar("600123123", "telefono"));
    }

    @Test
    void leeConClaveAnteriorYRotarEsIdempotente() {
        DatosPersonalesCryptoService cifradoAnterior = new DatosPersonalesCryptoService(CLAVE);
        DatosPersonalesCryptoService cifradoNuevo =
                new DatosPersonalesCryptoService(CLAVE_NUEVA, CLAVE);
        String valorAnterior = cifradoAnterior.cifrar("12345678A", "dniNif");

        assertEquals("12345678A", cifradoNuevo.descifrar(valorAnterior, "dniNif"));
        String valorRotado = cifradoNuevo.rotar(valorAnterior, "dniNif");
        assertNotEquals(valorAnterior, valorRotado);
        assertEquals("12345678A", cifradoNuevo.descifrar(valorRotado, "dniNif"));
        assertEquals(valorRotado, cifradoNuevo.rotar(valorRotado, "dniNif"));
    }

    @Test
    void rechazaUnValorCifradoManipulado() {
        String cifradoOriginal = cifrado.cifrar("600123123", "telefono");
        String manipulado = cifradoOriginal.substring(0, cifradoOriginal.length() - 1) + "A";

        assertThrows(IllegalStateException.class, () -> cifrado.descifrar(manipulado, "telefono"));
    }

    @Test
    void exigeUnaClaveBase64DeTreintaYDosBytes() {
        assertThrows(IllegalStateException.class, () -> new DatosPersonalesCryptoService(""));
        assertThrows(IllegalStateException.class, () -> new DatosPersonalesCryptoService("clave-invalida"));
        assertFalse(cifrado.estaCifrado("12345678A"));
    }
}
