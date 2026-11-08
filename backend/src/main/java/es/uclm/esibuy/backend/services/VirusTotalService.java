package es.uclm.esibuy.backend.services;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Analiza imágenes antes de publicarlas usando VirusTotal. */
@Service
public class VirusTotalService {

    private static final String API_URL = "https://www.virustotal.com/api/v3";
    private static final long INTERVALO_MS = 2_000L;
    private static final int MAX_INTENTOS = 30;

    private final RestClient cliente;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final boolean habilitado;
    private final boolean bloquearSiFalla;

    @Autowired
    public VirusTotalService(
            ObjectMapper objectMapper,
            @Value("${virustotal.api-key:}") String apiKey,
            @Value("${virustotal.enabled:false}") boolean habilitado,
            @Value("${virustotal.fail-closed:true}") boolean bloquearSiFalla) {
        this(RestClient.builder(), objectMapper, apiKey, habilitado, bloquearSiFalla);
    }

    VirusTotalService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${virustotal.api-key:}") String apiKey,
            @Value("${virustotal.enabled:false}") boolean habilitado,
            @Value("${virustotal.fail-closed:true}") boolean bloquearSiFalla) {
        this.cliente = restClientBuilder.baseUrl(API_URL).build();
        this.objectMapper = objectMapper;
        this.apiKey = Objects.requireNonNullElse(apiKey, "").trim();
        this.habilitado = habilitado;
        this.bloquearSiFalla = bloquearSiFalla;
    }

    public void verificar(byte[] contenido, String contentType) {
        if (!habilitado) {
            return;
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("VirusTotal está habilitado, pero falta VIRUSTOTAL_API_KEY.");
        }

        try {
            esperarResultado(subir(contenido, contentType));
        } catch (RuntimeException exception) {
            if (bloquearSiFalla) {
                throw new IllegalStateException(
                        "No se ha podido verificar la imagen con VirusTotal.", exception);
            }
        }
    }

    private String subir(byte[] contenido, String contentType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return "producto";
            }
        }).contentType(MediaType.parseMediaType(contentType));

        String respuesta = cliente.post()
                .uri("/files")
                .header("x-apikey", apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(String.class);

        return extraer(respuesta, "/data/id", "VirusTotal no devolvió el identificador del análisis.");
    }

    private void esperarResultado(String analysisId) {
        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            String respuesta = cliente.get()
                    .uri("/analyses/{id}", analysisId)
                    .header("x-apikey", apiKey)
                    .retrieve()
                    .body(String.class);

            if (analisisCompletado(respuesta)) {
                return;
            }

            dormir();
        }

        throw new IllegalStateException("VirusTotal no terminó el análisis dentro del tiempo esperado.");
    }

    private boolean analisisCompletado(String respuesta) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(respuesta);
        } catch (Exception exception) {
            throw new IllegalStateException("Respuesta no válida de VirusTotal.", exception);
        }
        if (!"completed".equalsIgnoreCase(raiz.at("/data/attributes/status").asText())) {
            return false;
        }
        int maliciosos = raiz.at("/data/attributes/stats/malicious").asInt();
        int sospechosos = raiz.at("/data/attributes/stats/suspicious").asInt();
        if (maliciosos > 0 || sospechosos > 0) {
            throw new IllegalArgumentException("La imagen ha sido rechazada por VirusTotal.");
        }
        return true;
    }

    private String extraer(String json, String ruta, String mensaje) {
        try {
            JsonNode valor = objectMapper.readTree(json).at(ruta);
            if (valor.isMissingNode() || valor.asText().isBlank()) {
                throw new IllegalStateException(mensaje);
            }
            return valor.asText();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(mensaje, exception);
        }
    }

    private void dormir() {
        try {
            Thread.sleep(INTERVALO_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("El análisis de VirusTotal fue interrumpido.", exception);
        }
    }
}
