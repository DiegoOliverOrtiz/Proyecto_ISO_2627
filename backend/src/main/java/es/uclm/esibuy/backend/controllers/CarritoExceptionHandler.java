package es.uclm.esibuy.backend.controllers;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = CarritoController.class)
public class CarritoExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accesoDenegado(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DuplicateKeyException.class})
    public ResponseEntity<ApiErrorResponse> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("El carrito se ha modificado. Inténtalo de nuevo."));
    }
}
