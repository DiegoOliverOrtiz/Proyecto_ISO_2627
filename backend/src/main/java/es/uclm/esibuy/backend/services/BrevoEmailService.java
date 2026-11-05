package es.uclm.esibuy.backend.services;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BrevoEmailService {

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
}