package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.DuplicateRegistrationException;
import es.uclm.esibuy.backend.exceptions.InvalidRegistrationException;
import es.uclm.esibuy.backend.exceptions.UsuarioNoEncontradoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class RegistrationExceptionHandler {

	@ExceptionHandler(DuplicateRegistrationException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateRegistration() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("Ya existe un usuario con los datos únicos indicados."));
	}

	@ExceptionHandler(InvalidRegistrationException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidRegistration(InvalidRegistrationException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(exception.getMessage(), exception.getFieldErrors()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.computeIfAbsent(fieldError.getField(), field -> new ArrayList<>())
					.add(fieldError.getDefaultMessage());
		}
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("Revisa los campos del formulario.", errors));
	}

	@ExceptionHandler(UsuarioNoEncontradoException.class)
	public ResponseEntity<ApiErrorResponse> handleUsuarioNoEncontrado(UsuarioNoEncontradoException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
	}
}