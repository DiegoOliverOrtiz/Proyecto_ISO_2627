package es.uclm.esibuy.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;


import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.dto.MetodoPagoRequest;
import es.uclm.esibuy.backend.dto.MetodoPagoResponse;
import es.uclm.esibuy.backend.dto.TipoMetodoPagoResponse;
import es.uclm.esibuy.backend.exceptions.MetodoPagoDuplicadoException;
import es.uclm.esibuy.backend.exceptions.MetodoPagoInvalidoException;
import es.uclm.esibuy.backend.services.PagoSimuladoService;
import es.uclm.esibuy.backend.exceptions.MetodoPagoNoEncontradoException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cliente/metodos-pago")
public class MetodoPagoController {

	private final PagoSimuladoService pagoSimuladoService;

	public MetodoPagoController(PagoSimuladoService pagoSimuladoService) {
		this.pagoSimuladoService = pagoSimuladoService;
	}

	@GetMapping("/tipos")
	public List<TipoMetodoPagoResponse> consultarTipos() {
		return pagoSimuladoService.consultarTiposDisponibles();
	}

	@GetMapping
	public List<MetodoPagoResponse> consultarMetodos(Authentication authentication) {
		return pagoSimuladoService.consultarMetodos(authentication.getName());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> accesoDenegado(AccessDeniedException excepcion) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ApiErrorResponse(excepcion.getMessage()));
	}
	
	public List<MetodoPagoResponse> consultarMetodosPago(Authentication authentication) {
		return pagoSimuladoService.consultarMetodosPago(authentication.getName());
	}

	@PostMapping
	public ResponseEntity<MetodoPagoResponse> registrarMetodoPago(
			Authentication authentication,
			@Valid @RequestBody MetodoPagoRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(pagoSimuladoService.registrarMetodoPago(authentication.getName(), request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminarMetodoPago(
			Authentication authentication,
			@PathVariable String id) {
		pagoSimuladoService.eliminarMetodoPago(authentication.getName(), id);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(MetodoPagoNoEncontradoException.class)
	public ResponseEntity<ApiErrorResponse> manejarMetodoPagoNoEncontrado(MetodoPagoNoEncontradoException excepcion) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse(excepcion.getMessage()));
	}

	@ExceptionHandler(MetodoPagoInvalidoException.class)
	public ResponseEntity<ApiErrorResponse> manejarMetodoPagoInvalido(MetodoPagoInvalidoException excepcion) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(excepcion.getMessage(), excepcion.getErrores()));
	}

	@ExceptionHandler(MetodoPagoDuplicadoException.class)
	public ResponseEntity<ApiErrorResponse> manejarMetodoPagoDuplicado(MetodoPagoDuplicadoException excepcion) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse(excepcion.getMessage()));
	}
}
