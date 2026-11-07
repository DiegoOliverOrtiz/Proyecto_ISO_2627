package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.web.client.ResourceAccessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import es.uclm.esibuy.backend.dao.LoginAttemptStore;
import es.uclm.esibuy.backend.exceptions.EmailOtpRateLimitException;

class EmailOtpServiceTests {

    private static final String EMAIL = "admin@example.com";
    private final BrevoEmailService brevo = org.mockito.Mockito.mock(BrevoEmailService.class);
    private final LoginAttemptStore attemptStore = org.mockito.Mockito.mock(LoginAttemptStore.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicInteger codes = new AtomicInteger(123455);
    private final EmailOtpService service = new EmailOtpService(
            Base64.getEncoder().encodeToString(new byte[32]),
            brevo,
            attemptStore, clock, codes::incrementAndGet);
    private MockHttpSession session;

    @BeforeEach
    void prepararSesion() {
        session = new MockHttpSession();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));
    }

    @Test
    void verificaElCodigoYLoConsumeUnaSolaVez() {
        service.iniciar(session, EMAIL);
        String codigo = capturarCodigoEnviado();

        assertEquals(
                EmailOtpService.ResultadoVerificacion.VALIDO,
                service.verificar(session, EMAIL, codigo));
        assertEquals(
                EmailOtpService.ResultadoVerificacion.CADUCADO,
                service.verificar(session, EMAIL, codigo));
    }

    @Test
    void bloqueaElRetoTrasCincoCodigosIncorrectos() {
        service.iniciar(session, EMAIL);
        String codigoValido = capturarCodigoEnviado();
        String codigoIncorrecto = codigoValido.equals("000000") ? "999999" : "000000";

        for (int intento = 1; intento < EmailOtpService.MAX_INTENTOS; intento++) {
            assertEquals(
                    EmailOtpService.ResultadoVerificacion.INVALIDO,
                    service.verificar(session, EMAIL, codigoIncorrecto));
        }

        assertEquals(
                EmailOtpService.ResultadoVerificacion.BLOQUEADO,
                service.verificar(session, EMAIL, codigoIncorrecto));
    }

    @Test
    void noPermiteReenviarAntesDeUnMinuto() {
        service.iniciar(session, EMAIL);

        var resultado = service.reenviar(session, EMAIL);

        assertEquals(EmailOtpService.EstadoReenvio.ESPERA, resultado.estado());
        assertEquals(1, org.mockito.Mockito.mockingDetails(brevo).getInvocations().size());
        verify(brevo).enviarCodigoAutenticacion(anyString(), anyString());
    }

    @Test
    void noEnviaCodigoSiLaClaveHmacNoEstaConfiguradaCorrectamente() {
        EmailOtpService sinClave = new EmailOtpService("", brevo, attemptStore);

        assertThrows(IllegalStateException.class, () -> sinClave.iniciar(session, EMAIL));

        verifyNoInteractions(brevo);
    }

    @Test
    void aplicaElLimiteGlobalDeEnviosAunqueSeCreeOtroReto() {
        when(attemptStore.reservarEnvioOtp(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(60);

        assertThrows(EmailOtpRateLimitException.class, () -> service.iniciar(session, EMAIL));

        verifyNoInteractions(brevo);
    }

    private void avanzar(int segundos) {
        Instant siguiente = clock.instant().plusSeconds(segundos);
        when(clock.instant()).thenReturn(siguiente);
    }

    @Test
    void caducaExactamenteEnElLimite() {
        service.iniciar(session, EMAIL);
        avanzar(600);
        assertEquals(EmailOtpService.ResultadoVerificacion.CADUCADO,
                service.verificar(session, EMAIL, "123456"));
        assertNull(session.getAttribute(EmailOtpService.SESION_RETO_EMAIL));
    }

    @Test
    void reenvioInvalidaCodigoAnteriorYConservaCaducidadOriginal() {
        service.iniciar(session, EMAIL);
        avanzar(60);
        assertEquals(EmailOtpService.EstadoReenvio.ENVIADO, service.reenviar(session, EMAIL).estado());
        assertEquals(EmailOtpService.ResultadoVerificacion.INVALIDO, service.verificar(session, EMAIL, "123456"));
        assertEquals(EmailOtpService.ResultadoVerificacion.VALIDO, service.verificar(session, EMAIL, "123457"));
        verify(brevo).enviarCodigoAutenticacion(EMAIL, "123457");
    }

    @Test
    void reenviarNoAmpliaLaCaducidad() {
        service.iniciar(session, EMAIL);
        avanzar(60);
        service.reenviar(session, EMAIL);
        avanzar(540);
        assertEquals(EmailOtpService.EstadoReenvio.CADUCADO, service.reenviar(session, EMAIL).estado());
        verify(brevo, times(2)).enviarCodigoAutenticacion(eq(EMAIL), anyString());
    }

    @Test
    void limitaTresEnviosYConservaIntentosTrasReenviar() {
        service.iniciar(session, EMAIL);
        for (int i = 0; i < 4; i++) service.verificar(session, EMAIL, "000000");
        avanzar(60);
        service.reenviar(session, EMAIL);
        avanzar(60);
        service.reenviar(session, EMAIL);
        avanzar(60);
        assertEquals(EmailOtpService.EstadoReenvio.LIMITE, service.reenviar(session, EMAIL).estado());
        assertEquals(EmailOtpService.ResultadoVerificacion.BLOQUEADO, service.verificar(session, EMAIL, "000000"));
        assertEquals(EmailOtpService.ResultadoVerificacion.CADUCADO, service.verificar(session, EMAIL, "123458"));
        verify(brevo, times(3)).enviarCodigoAutenticacion(eq(EMAIL), anyString());
    }

    @Test
    void falloInicialBrevoNoCreaRetoYPermiteReintento() {
        doThrow(new ResourceAccessException("No disponible")).doNothing()
                .when(brevo).enviarCodigoAutenticacion(eq(EMAIL), anyString());
        assertThrows(ResourceAccessException.class, () -> service.iniciar(session, EMAIL));
        assertNull(session.getAttribute(EmailOtpService.SESION_RETO_EMAIL));
        assertEquals(EmailOtpService.ResultadoVerificacion.CADUCADO, service.verificar(session, EMAIL, "123456"));
        assertEquals(EmailOtpService.EstadoReenvio.ENVIADO, service.reenviar(session, EMAIL).estado());
        assertEquals(EmailOtpService.ResultadoVerificacion.VALIDO, service.verificar(session, EMAIL, "123457"));
        verify(attemptStore, times(2)).reservarEnvioOtp(eq(EMAIL), any());
    }

    @Test
    void falloDeReenvioConservaElCodigoAnterior() {
        service.iniciar(session, EMAIL);
        avanzar(60);
        doThrow(new ResourceAccessException("No disponible"))
                .when(brevo).enviarCodigoAutenticacion(eq(EMAIL), anyString());
        assertThrows(ResourceAccessException.class, () -> service.reenviar(session, EMAIL));
        assertEquals(EmailOtpService.ResultadoVerificacion.VALIDO, service.verificar(session, EMAIL, "123456"));
    }

    @Test
    void limiteGlobalSeAplicaEntreSesionesYAnteFallos() {
        when(attemptStore.reservarEnvioOtp(eq(EMAIL), any())).thenReturn(0, 60);
        service.iniciar(session, EMAIL);
        MockHttpSession otra = new MockHttpSession();
        assertThrows(EmailOtpRateLimitException.class, () -> service.iniciar(otra, EMAIL));
        assertNull(otra.getAttribute(EmailOtpService.SESION_RETO_EMAIL));
        verify(brevo, times(1)).enviarCodigoAutenticacion(eq(EMAIL), anyString());
    }

    @Test
    void codigoNoSirveEnOtraSesionNiConOtroCorreo() {
        service.iniciar(session, EMAIL);
        MockHttpSession otra = new MockHttpSession();
        // Incluso copiando el atributo público de sesión, el HMAC está ligado al ID.
        otra.setAttribute(EmailOtpService.SESION_RETO_EMAIL, session.getAttribute(EmailOtpService.SESION_RETO_EMAIL));
        assertEquals(EmailOtpService.ResultadoVerificacion.INVALIDO, service.verificar(otra, EMAIL, "123456"));
        assertEquals(EmailOtpService.ResultadoVerificacion.CADUCADO, service.verificar(session, "otro@example.com", "123456"));
        assertEquals(EmailOtpService.ResultadoVerificacion.CADUCADO, service.verificar(new MockHttpSession(), EMAIL, "123456"));
    }

    private String capturarCodigoEnviado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(brevo).enviarCodigoAutenticacion(org.mockito.ArgumentMatchers.eq(EMAIL), captor.capture());
        return captor.getValue();
    }
}
