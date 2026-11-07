package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class BrevoEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrevoEmailService.class);

    private final String apiKey;
    private final String emailRemitente;
    private final String nombreRemitente;
    private final RestClient restClient;

    public BrevoEmailService(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.from-email:}") String emailRemitente,
            @Value("${brevo.from-name:ESIBuy}") String nombreRemitente) {
        this.apiKey = apiKey;
        this.emailRemitente = emailRemitente;
        this.nombreRemitente = nombreRemitente;
        this.restClient = RestClient.create();
    }

    /**
     * Envía el correo de recuperación fuera del hilo de la petición, para que
     * la respuesta no tarde más cuando el correo existe. Los fallos solo se
     * registran: el cliente siempre recibe la misma respuesta genérica.
     */
    @Async
    public void enviarRecuperacionEnSegundoPlano(String destinatario, String enlace) {
        try {
            enviarRecuperacion(destinatario, enlace);
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.error("No se pudo enviar el correo de recuperación mediante Brevo.", exception);
        }
    }

    public void enviarRecuperacion(String destinatario, String enlace) {
        if (apiKey.isBlank() || emailRemitente.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la API de Brevo o el remitente.");
        }

        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of(
                        "name", nombreRemitente,
                        "email", emailRemitente),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Recupera tu contraseña de ESIBuy",
                "textContent", """
                        Hemos recibido una solicitud para cambiar la contraseña de tu cuenta ESIBuy.

                        Para elegir una contraseña nueva, abre este enlace:
                        %s

                        Si no solicitaste el cambio, ignora este correo.
                        """.formatted(enlace));

        restClient.post()
                .uri("https://api.brevo.com/v3/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
    }

    public void enviarCodigoAutenticacion(String destinatario, String codigo) {
        if (apiKey.isBlank() || emailRemitente.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la API de Brevo o el remitente.");
        }

        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of(
                        "name", nombreRemitente,
                        "email", emailRemitente),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Código de verificación de ESIBuy",
                "textContent", """
                        Tu código de verificación de ESIBuy es: %s

                        Caduca en 10 minutos y solo se puede utilizar una vez.
                        Si no estás intentando iniciar sesión, ignora este correo.
                        """.formatted(codigo));

        restClient.post()
                .uri("https://api.brevo.com/v3/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
    }
}