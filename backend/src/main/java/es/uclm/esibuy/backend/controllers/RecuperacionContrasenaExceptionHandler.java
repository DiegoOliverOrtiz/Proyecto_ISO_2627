package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.TokenRecuperacionInvalidoException;

@RestControllerAdvice(assignableTypes = RecuperacionContrasenaController.class)
public class RecuperacionContrasenaExceptionHandler {

    @ExceptionHandler(TokenRecuperacionInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> tokenInvalido() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "El enlace de recuperación no es válido o ha caducado."));
    }
}