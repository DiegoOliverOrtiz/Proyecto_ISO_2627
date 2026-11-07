package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;

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
    private final EmailOtpService service = new EmailOtpService(
            Base64.getEncoder().encodeToString(new byte[32]),
            brevo,
            attemptStore);
    private MockHttpSession session;

    @BeforeEach
    void prepararSesion() {
        session = new MockHttpSession();
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

    private String capturarCodigoEnviado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(brevo).enviarCodigoAutenticacion(org.mockito.ArgumentMatchers.eq(EMAIL), captor.capture());
        return captor.getValue();
    }
}
