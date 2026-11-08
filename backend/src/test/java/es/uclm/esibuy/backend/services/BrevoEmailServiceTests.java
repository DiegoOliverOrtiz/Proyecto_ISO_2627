package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import es.uclm.esibuy.backend.config.AsyncConfiguration;

class BrevoEmailServiceTests {

    @Test
    void elEnvioDeRecuperacionSeHaceEnSegundoPlano() throws Exception {
        // A23: la respuesta de la solicitud no espera a la llamada a Brevo.
        assertTrue(BrevoEmailService.class
                .getMethod("enviarRecuperacionEnSegundoPlano", String.class, String.class)
                .isAnnotationPresent(Async.class));
        assertTrue(AsyncConfiguration.class.isAnnotationPresent(EnableAsync.class));
    }

    @Test
    void unFalloAlEnviarEnSegundoPlanoNoSePropaga() {
        BrevoEmailService servicio = new BrevoEmailService("", "", "ESIBuy");

        assertDoesNotThrow(() -> servicio.enviarRecuperacionEnSegundoPlano(
                "cliente@example.com", "https://esibuy.example/recuperar-contrasena?token=x"));
    }

    @Test
    void elEnvioDirectoSigueAvisandoSiFaltaLaConfiguracion() {
        BrevoEmailService servicio = new BrevoEmailService("", "", "ESIBuy");

        assertThrows(IllegalStateException.class, () -> servicio.enviarRecuperacion(
                "cliente@example.com", "https://esibuy.example/recuperar-contrasena?token=x"));
    }
}
