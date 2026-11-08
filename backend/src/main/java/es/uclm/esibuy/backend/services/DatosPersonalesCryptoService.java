package es.uclm.esibuy.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatosPersonalesCryptoService {

    static final String PREFIJO_CIFRADO = "enc:v1:";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_CLAVE = 32;

    private final byte[] claveActual;
    private final byte[] claveAnterior;
    private final SecureRandom generadorAleatorio = new SecureRandom();

    public DatosPersonalesCryptoService(String claveBase64) {
        this(claveBase64, "");
    }

    @Autowired
    public DatosPersonalesCryptoService(
            @Value("${PERSONAL_DATA_ENCRYPTION_KEY:}") String claveBase64,
            @Value("${PERSONAL_DATA_PREVIOUS_ENCRYPTION_KEY:}") String claveAnteriorBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            throw new IllegalStateException("PERSONAL_DATA_ENCRYPTION_KEY debe estar configurada.");
        }

        claveActual = decodificarClave(claveBase64, "PERSONAL_DATA_ENCRYPTION_KEY");
        claveAnterior = claveAnteriorBase64 == null || claveAnteriorBase64.isBlank()
                ? null
                : decodificarClave(claveAnteriorBase64, "PERSONAL_DATA_PREVIOUS_ENCRYPTION_KEY");
    }

    private static byte[] decodificarClave(String claveBase64, String nombreVariable) {
        try {
            byte[] clave = Base64.getDecoder().decode(claveBase64);
            if (clave.length != LONGITUD_CLAVE) {
                throw new IllegalArgumentException();
            }
            return clave;
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalStateException(
                    nombreVariable + " debe ser una clave Base64 de 32 bytes.",
                    excepcion);
        }
    }

    public String cifrar(String valor, String campo) {
        if (valor == null) {
            return null;
        }

        try {
            byte[] iv = new byte[LONGITUD_IV];
            generadorAleatorio.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveActual, "AES"),
                    new GCMParameterSpec(128, iv));
            cifrador.updateAAD(campo.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifrador.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return PREFIJO_CIFRADO
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(cifrado);
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException("No se pudo cifrar un dato personal.", excepcion);
        }
    }

    public String descifrar(String valor, String campo) {
        if (valor == null || !estaCifrado(valor)) {
            return valor;
        }

        try {
            return descifrarConClave(valor, campo, claveActual);
        } catch (IllegalStateException claveActualInvalida) {
            if (claveAnterior == null) {
                throw claveActualInvalida;
            }
            try {
                return descifrarConClave(valor, campo, claveAnterior);
            } catch (IllegalStateException claveAnteriorInvalida) {
                claveActualInvalida.addSuppressed(claveAnteriorInvalida);
                throw claveActualInvalida;
            }
        }
    }

    public String rotar(String valor, String campo) {
        if (valor == null || !estaCifrado(valor)) {
            return valor;
        }

        try {
            descifrarConClave(valor, campo, claveActual);
            return valor;
        } catch (IllegalStateException claveActualInvalida) {
            if (claveAnterior == null) {
                throw new IllegalStateException(
                        "La rotación requiere PERSONAL_DATA_PREVIOUS_ENCRYPTION_KEY.",
                        claveActualInvalida);
            }
            String textoPlano = descifrarConClave(valor, campo, claveAnterior);
            return cifrar(textoPlano, campo);
        }
    }

    private String descifrarConClave(String valor, String campo, byte[] clave) {
        try {
            String[] partes = valor.substring(PREFIJO_CIFRADO.length()).split(":", 2);
            if (partes.length != 2) {
                throw new IllegalArgumentException();
            }
            Cipher descifrador = Cipher.getInstance("AES/GCM/NoPadding");
            descifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(partes[0])));
            descifrador.updateAAD(campo.getBytes(StandardCharsets.UTF_8));
            return new String(
                    descifrador.doFinal(Base64.getDecoder().decode(partes[1])),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException excepcion) {
            throw new IllegalStateException("No se pudo descifrar un dato personal almacenado.", excepcion);
        }
    }

    public boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIJO_CIFRADO);
    }

    public boolean tieneClaveAnterior() {
        return claveAnterior != null;
    }

    public boolean tieneClavesDistintas() {
        return claveAnterior != null && !Arrays.equals(claveActual, claveAnterior);
    }
}
