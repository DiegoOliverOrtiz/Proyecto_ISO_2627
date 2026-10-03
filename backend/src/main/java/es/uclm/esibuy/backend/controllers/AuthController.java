package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CsrfTokenResponse;
import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.dto.LoginResponse;
import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse obtenerTokenCsrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> iniciarSesion(
            @Valid @RequestBody LoginRequest peticion,
            HttpServletRequest request,
            HttpServletResponse response) {
        Usuario usuarioValidado = authService.autenticar(peticion.email(), peticion.password());
        String rol = obtenerRol(usuarioValidado);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                usuarioValidado.getEmail(),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + rol)));

        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setMaxInactiveInterval(30 * 60);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new LoginResponse(usuarioValidado.getEmail(), rol));
    }

    @GetMapping("/sesion-actual")
    public ResponseEntity<LoginResponse> obtenerSesionActual(Authentication authentication) {
        String rol = authentication.getAuthorities().iterator().next().getAuthority()
                .substring("ROLE_".length());
        return ResponseEntity.ok(new LoginResponse(authentication.getName(), rol));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> manejarCredencialesIncorrectas(BadCredentialsException excepcion) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(excepcion.getMessage()));
    }

    @ExceptionHandler(AccountPendingException.class)
    public ResponseEntity<ApiErrorResponse> manejarCuentaPendiente(AccountPendingException excepcion) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(excepcion.getMessage()));
    }

    private String obtenerRol(Usuario usuario) {
        if (usuario instanceof Administrador) {
            return "ADMIN";
        }
        if (usuario instanceof Cliente) {
            return "CLIENTE";
        }
        if (usuario instanceof Vendedor) {
            return "VENDEDOR";
        }
        throw new IllegalStateException("Tipo de usuario no soportado para el inicio de sesión.");
    }
}