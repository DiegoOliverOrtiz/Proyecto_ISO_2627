package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dao.TotpSecurityStore;
import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.TooManyTwoFactorAttemptsException;

@RestControllerAdvice(assignableTypes = {AuthController.class, PerfilController.class})
public class TwoFactorExceptionHandler {
    @ExceptionHandler(TooManyTwoFactorAttemptsException.class)
    public ResponseEntity<ApiErrorResponse> limitarIntentos(TooManyTwoFactorAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Integer.toString(TotpSecurityStore.WINDOW_SECONDS))
                .body(new ApiErrorResponse(exception.getMessage()));
    }
}
