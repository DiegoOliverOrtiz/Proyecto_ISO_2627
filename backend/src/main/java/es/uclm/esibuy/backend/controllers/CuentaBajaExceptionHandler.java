package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.CuentaNoEncontradaException;
import es.uclm.esibuy.backend.exceptions.VendedorConPedidosEnCursoException;

@RestControllerAdvice(assignableTypes = { ClienteController.class, VendedorController.class })
public class CuentaBajaExceptionHandler {
    @ExceptionHandler(VendedorConPedidosEnCursoException.class)
    public ResponseEntity<ApiErrorResponse> pedidosEnCurso(VendedorConPedidosEnCursoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<ApiErrorResponse> cuentaNoEncontrada(CuentaNoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }
}
