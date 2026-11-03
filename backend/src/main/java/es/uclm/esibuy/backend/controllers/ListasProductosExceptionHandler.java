package es.uclm.esibuy.backend.controllers;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.ListaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.NombreListaDuplicadoException;
import es.uclm.esibuy.backend.exceptions.OperacionListaNoPermitidaException;

@RestControllerAdvice(assignableTypes = ListasProductosController.class)
public class ListasProductosExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accesoDenegado(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(ListaNoEncontradaException.class)
    public ResponseEntity<ApiErrorResponse> listaNoEncontrada(ListaNoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({NombreListaDuplicadoException.class, OperacionListaNoPermitidaException.class})
    public ResponseEntity<ApiErrorResponse> conflicto(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DuplicateKeyException.class})
    public ResponseEntity<ApiErrorResponse> modificacionConcurrente() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("La lista se ha modificado. Inténtalo de nuevo."));
    }
}
