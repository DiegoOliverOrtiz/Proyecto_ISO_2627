package es.uclm.esibuy.backend.services;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.IntSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.LoginAttemptStore;
import es.uclm.esibuy.backend.exceptions.EmailOtpRateLimitException;
import jakarta.servlet.http.HttpSession;

@Service
public class EmailOtpService {

    public static final String SESION_RETO_EMAIL = "ESIBUY_3FA_RETO_EMAIL";
    public static final int DURACION_RETO_SEGUNDOS = 10 * 60;
    public static final int MAX_INTENTOS = 5;
    public static final int MAX_ENVIOS = 3;
    public static final int INTERVALO_REENVIO_SEGUNDOS = 60;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Clock clock;
    private final IntSupplier generador;
    private final String claveHmac;
    private final BrevoEmailService brevoEmailService;
    private final LoginAttemptStore loginAttemptStore;

    @Autowired
    public EmailOtpService(
            @Value("${app.security.email-otp.hmac-key:}") String claveHmac,
            BrevoEmailService brevoEmailService,
            LoginAttemptStore loginAttemptStore) {
        this(claveHmac, brevoEmailService, loginAttemptStore, Clock.systemUTC(),
                () -> SECURE_RANDOM.nextInt(1_000_000));
    }

    EmailOtpService(String claveHmac, BrevoEmailService brevoEmailService,
            LoginAttemptStore loginAttemptStore, Clock clock, IntSupplier generador) {
        this.clock = clock;
        this.generador = generador;
        this.claveHmac = claveHmac;
        this.brevoEmailService = brevoEmailService;
        this.loginAttemptStore = loginAttemptStore;
    }

    public void iniciar(HttpSession sesion, String email) {
        String codigo = generarCodigo();
        String hmac = calcularHmac(sesion.getId(), email, codigo);
        Instant ahora = clock.instant();
        reservarEnvio(email, ahora);
        enviar(sesion, email, codigo);
        sesion.setAttribute(
                SESION_RETO_EMAIL,
                new RetoEmail(
                        email,
                        hmac,
                        ahora.plusSeconds(DURACION_RETO_SEGUNDOS),
                        ahora,
                        1,
                        0));
    }

    public ResultadoReenvio reenviar(HttpSession sesion, String email) {
        RetoEmail reto = obtenerReto(sesion);
        if (reto == null) {
            iniciar(sesion, email);
            return new ResultadoReenvio(EstadoReenvio.ENVIADO, 0);
        }
        if (!reto.email().equals(email) || !clock.instant().isBefore(reto.caducidad())) {
            limpiar(sesion);
            return new ResultadoReenvio(EstadoReenvio.CADUCADO, 0);
        }
        if (reto.envios() >= MAX_ENVIOS) {
            return new ResultadoReenvio(EstadoReenvio.LIMITE, 0);
        }

        int espera = segundosRestantes(reto.ultimoEnvio().plusSeconds(INTERVALO_REENVIO_SEGUNDOS));
        if (espera > 0) {
            return new ResultadoReenvio(EstadoReenvio.ESPERA, espera);
        }

        String codigo = generarCodigo();
        String hmac = calcularHmac(sesion.getId(), email, codigo);
        Instant ahora = clock.instant();
        reservarEnvio(email, ahora);
        enviar(sesion, email, codigo);
        sesion.setAttribute(
                SESION_RETO_EMAIL,
                new RetoEmail(
                        email,
                        hmac,
                        reto.caducidad(),
                        ahora,
                        reto.envios() + 1,
                        reto.intentos()));
        return new ResultadoReenvio(EstadoReenvio.ENVIADO, 0);
    }

    public ResultadoVerificacion verificar(HttpSession sesion, String email, String codigo) {
        RetoEmail reto = obtenerReto(sesion);
        if (reto == null || !reto.email().equals(email) || !clock.instant().isBefore(reto.caducidad())) {
            limpiar(sesion);
            return ResultadoVerificacion.CADUCADO;
        }

        boolean formatoValido = codigo != null && codigo.matches("\\d{6}");
        boolean codigoValido = formatoValido
                && MessageDigest.isEqual(
                        HexFormat.of().parseHex(reto.hmac()),
                        HexFormat.of().parseHex(calcularHmac(sesion.getId(), email, codigo)));
        if (codigoValido) {
            limpiar(sesion);
            return ResultadoVerificacion.VALIDO;
        }

        int intentos = reto.intentos() + 1;
        if (intentos >= MAX_INTENTOS) {
            limpiar(sesion);
            return ResultadoVerificacion.BLOQUEADO;
        }
        sesion.setAttribute(
                SESION_RETO_EMAIL,
                new RetoEmail(
                        reto.email(),
                        reto.hmac(),
                        reto.caducidad(),
                        reto.ultimoEnvio(),
                        reto.envios(),
                        intentos));
        return ResultadoVerificacion.INVALIDO;
    }

    public void limpiar(HttpSession sesion) {
        if (sesion != null) {
            sesion.removeAttribute(SESION_RETO_EMAIL);
        }
    }

    private void enviar(HttpSession sesion, String email, String codigo) {
        if (sesion.getId() == null || email == null || email.isBlank()) {
            throw new IllegalStateException("No se puede iniciar la verificación por correo.");
        }
        brevoEmailService.enviarCodigoAutenticacion(email, codigo);
    }

    private void reservarEnvio(String email, Instant ahora) {
        int espera = loginAttemptStore.reservarEnvioOtp(email, ahora);
        if (espera > 0) {
            throw new EmailOtpRateLimitException(espera);
        }
    }

    private RetoEmail obtenerReto(HttpSession sesion) {
        Object reto = sesion.getAttribute(SESION_RETO_EMAIL);
        return reto instanceof RetoEmail emailReto ? emailReto : null;
    }

    private String generarCodigo() {
        return String.format(Locale.ROOT, "%06d", generador.getAsInt());
    }

    private String calcularHmac(String idSesion, String email, String codigo) {
        try {
            byte[] clave = Base64.getDecoder().decode(claveHmac);
            if (clave.length != 32) {
                throw new IllegalStateException(
                        "APP_SECURITY_EMAIL_OTP_KEY debe ser una clave Base64 de 32 bytes.");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            byte[] entrada = (idSesion + "\n" + email + "\n" + codigo)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(entrada));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "APP_SECURITY_EMAIL_OTP_KEY debe ser una clave Base64 válida.",
                    exception);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo proteger el código de verificación.", exception);
        }
    }

    private int segundosRestantes(Instant instante) {
        long milisegundos = Duration.between(clock.instant(), instante).toMillis();
        return (int) Math.max(0, (milisegundos + 999) / 1000);
    }

    public enum ResultadoVerificacion {
        VALIDO,
        INVALIDO,
        BLOQUEADO,
        CADUCADO
    }

    public enum EstadoReenvio {
        ENVIADO,
        ESPERA,
        LIMITE,
        CADUCADO
    }

    public record ResultadoReenvio(EstadoReenvio estado, int segundosEspera) {
    }

    private record RetoEmail(
            String email,
            String hmac,
            Instant caducidad,
            Instant ultimoEnvio,
            int envios,
            int intentos) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
