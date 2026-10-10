package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> manejarCredencialesIncorrectas(BadCredentialsException excepcion) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(excepcion.getMessage()));
    }

    @ExceptionHandler(AccountPendingException.class)
    public ResponseEntity<ApiErrorResponse> manejarCuentaPendiente(AccountPendingException excepcion) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(excepcion.getMessage()));
    }
}
