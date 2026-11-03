package es.uclm.esibuy.backend.controllers;

import org.springframework.http.ResponseEntity;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

import es.uclm.esibuy.backend.dto.PerfilResponse;
import es.uclm.esibuy.backend.dto.ModificarUsuarioRequest;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.dto.TwoFactorConfirmationResponse;
import es.uclm.esibuy.backend.dto.TwoFactorSetupResponse;
import es.uclm.esibuy.backend.services.PerfilService;
import es.uclm.esibuy.backend.services.TotpService;
import es.uclm.esibuy.backend.model.Usuario;

@RestController
@RequestMapping("/usuarios")
public class PerfilController {

    private final PerfilService perfilService;
    private final TotpService totpService;

    public PerfilController(PerfilService perfilService, TotpService totpService) {
        this.perfilService = perfilService;
        this.totpService = Objects.requireNonNull(totpService);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/perfil")
    public ResponseEntity<PerfilResponse> consultarPerfil(Authentication authentication) {
        return ResponseEntity.ok(perfilService.consultarPerfil(authentication.getName()));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/perfil")
    public ResponseEntity<PerfilResponse> modificarPerfil(
            Authentication authentication,
            @RequestBody ModificarUsuarioRequest request) {

        return ResponseEntity.ok(
                perfilService.modificarPerfil(
                        authentication.getName(),
                        request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/2fa/configurar")
    public ResponseEntity<TwoFactorSetupResponse> configurarSegundoFactor(
            Authentication authentication) {
        Usuario usuario = totpService.buscarUsuario(authentication.getName());
        if (usuario.isTotpActivado()) {
            return ResponseEntity.ok(new TwoFactorSetupResponse(null, true));
        }
        String secret = totpService.generarSecreto();
        totpService.guardarSecretoPendiente(usuario, secret);
        String otpauthUri = totpService.crearUriAlta(usuario.getEmail(), secret);
        return ResponseEntity.ok(new TwoFactorSetupResponse(
                otpauthUri, totpService.generarQrDataUrl(otpauthUri), false));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/2fa/confirmar")
    public ResponseEntity<TwoFactorConfirmationResponse> confirmarSegundoFactor(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody TwoFactorCodeRequest request) {
        Usuario usuario = totpService.buscarUsuario(authentication.getName());
        return ResponseEntity.ok(totpService.confirmarConfiguracion(usuario, request.code()));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/2fa")
    public ResponseEntity<Void> desactivarSegundoFactor(Authentication authentication,
            @jakarta.validation.Valid @RequestBody TwoFactorCodeRequest request) {
        totpService.desactivar(totpService.buscarUsuario(authentication.getName()), request.code());
        return ResponseEntity.noContent().build();
    }
}
