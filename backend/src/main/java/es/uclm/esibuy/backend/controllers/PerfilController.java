package es.uclm.esibuy.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.services.PerfilService;

@RestController
@RequestMapping("/usuarios")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/perfil")
    public ResponseEntity<PerfilResponse> consultarPerfil(Authentication authentication) {
        return ResponseEntity.ok(perfilService.consultarPerfil(authentication.getName()));
    }
}
