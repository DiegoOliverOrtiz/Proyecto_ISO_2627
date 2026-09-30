package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;

@RestControllerAdvice
public class RegistrationExceptionHandler {

	@ExceptionHandler(DuplicateRegistrationException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateRegistration() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("Ya existe un usuario con los datos únicos indicados."));
	}

	@ExceptionHandler({InvalidRegistrationException.class, MethodArgumentNotValidException.class})
	public ResponseEntity<ApiErrorResponse> handleInvalidRegistration() {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("Los datos de registro o la contraseña no cumplen los requisitos."));
	}
}