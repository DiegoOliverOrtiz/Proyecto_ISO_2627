package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.RegistroAdministradorRequest;
import es.uclm.esibuy.backend.dto.RegistroUsuarioResponse;
import es.uclm.esibuy.backend.services.AdministradorService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/administrador")
public class AdministradorController {

	private final AdministradorService administradorService;

	public AdministradorController(AdministradorService administradorService) {
		this.administradorService = administradorService;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/register")
	public ResponseEntity<RegistroUsuarioResponse> register(
			@Valid @RequestBody RegistroAdministradorRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(administradorService.registrar(request));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping("/usuarios/{id}/activar")
	public ResponseEntity<Void> activarUsuario(@PathVariable String id) {
		administradorService.activarUsuario(id);
		return ResponseEntity.noContent().build();
	}
}