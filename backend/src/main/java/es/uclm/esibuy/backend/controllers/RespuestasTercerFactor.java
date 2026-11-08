package es.uclm.esibuy.backend.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.EmailOtpRateLimitException;
import es.uclm.esibuy.backend.services.EmailOtpService;
import jakarta.servlet.http.HttpSession;

/**
 * Respuestas HTTP de los endpoints del tercer factor, separadas de {@link AuthController}.
 */
final class RespuestasTercerFactor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RespuestasTercerFactor.class);
    private static final String CABECERA_REINTENTO = "Retry-After";

    private RespuestasTercerFactor() {
    }

    static <T> ResponseEntity<T> noAutenticado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    static <T> ResponseEntity<T> caducado() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    static <T> ResponseEntity<T> aceptado(T cuerpo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(cuerpo);
    }

    static ResponseEntity<?> verificar(
            EmailOtpService emailOtpService,
            HttpSession sesion,
            String email,
            String codigo,
            Runnable limpiarPendiente) {
        return errorDeVerificacion(emailOtpService.verificar(sesion, email, codigo), limpiarPendiente);
    }

    /** Devuelve la respuesta de error de la verificación, o {@code null} si el código es válido. */
    static ResponseEntity<?> errorDeVerificacion(
            EmailOtpService.ResultadoVerificacion resultado,
            Runnable limpiarPendiente) {
        switch (resultado) {
            case INVALIDO:
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiErrorResponse("El código de verificación no es válido."));
            case BLOQUEADO:
                limpiarPendiente.run();
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(CABECERA_REINTENTO, Integer.toString(EmailOtpService.DURACION_RETO_SEGUNDOS))
                        .body(new ApiErrorResponse(
                                "Se ha alcanzado el límite de intentos. Inicia sesión de nuevo más tarde."));
            case CADUCADO:
                limpiarPendiente.run();
                return ResponseEntity.status(HttpStatus.GONE).build();
            default:
                return null;
        }
    }

    static ResponseEntity<?> reenviar(
            EmailOtpService emailOtpService,
            HttpSession sesion,
            String email,
            Runnable limpiarPendiente) {
        try {
            EmailOtpService.ResultadoReenvio resultado = emailOtpService.reenviar(sesion, email);
            return switch (resultado.estado()) {
                case ENVIADO -> ResponseEntity.noContent().build();
                case ESPERA -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(CABECERA_REINTENTO, Integer.toString(resultado.segundosEspera()))
                        .body(new ApiErrorResponse(
                                "Espera antes de solicitar otro código de verificación."));
                case LIMITE -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiErrorResponse(
                                "Has alcanzado el límite de envíos para este inicio de sesión."));
                case CADUCADO -> {
                    limpiarPendiente.run();
                    yield ResponseEntity.status(HttpStatus.GONE).build();
                }
            };
        } catch (EmailOtpRateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(CABECERA_REINTENTO, Integer.toString(exception.getRetryAfterSeconds()))
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.error("No se pudo enviar el código del tercer factor por correo.", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiErrorResponse(
                            "No se pudo enviar el código. Inténtalo de nuevo más tarde."));
        }
    }

    /** Envía el código inicial; devuelve {@code false} si no se pudo enviar. */
    static boolean iniciar(EmailOtpService emailOtpService, HttpSession sesion, String email, String rol) {
        try {
            emailOtpService.iniciar(sesion, email);
            return true;
        } catch (EmailOtpRateLimitException exception) {
            emailOtpService.limpiar(sesion);
            LOGGER.warn("Se ha alcanzado el límite global de envíos para el tercer factor.");
            return false;
        } catch (RestClientException | IllegalStateException exception) {
            emailOtpService.limpiar(sesion);
            LOGGER.error("No se pudo iniciar el tercer factor por correo para el rol {}.", rol, exception);
            return false;
        }
    }
}
