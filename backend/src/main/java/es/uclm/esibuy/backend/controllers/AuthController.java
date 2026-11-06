package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CsrfTokenResponse;
import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.dto.LoginResponse;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class AuthController {

    private static final String PREFIJO_ROL = "ROLE_";
    static final String SESION_EMAIL_2FA_PENDIENTE = "ESIBUY_2FA_EMAIL_PENDIENTE";
    static final String SESION_2FA_CADUCIDAD = "ESIBUY_2FA_CADUCIDAD";
    // Nombre de un atributo de sesión; su valor no es una credencial.
    static final String SESION_2FA_SECRETO = "ESIBUY_2FA_SECRETO"; // gitleaks:allow

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SesionService sesionService;
    private final TotpService totpService;

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository,
            SesionService sesionService,
            TotpService totpService) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.sesionService = sesionService;
        this.totpService = Objects.requireNonNull(totpService);
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
        Usuario usuarioValidado = authService.autenticar(
            peticion.email(), peticion.password(), request.getRemoteAddr());
        String rol = obtenerRol(usuarioValidado);
        if (usuarioValidado.isTotpActivado()) {
            HttpSession anterior = request.getSession(false);
            if (anterior != null) {
                anterior.invalidate();
            }
            SecurityContextHolder.clearContext();
            HttpSession sesionPendiente = request.getSession(true);
            sesionPendiente.setAttribute(SESION_EMAIL_2FA_PENDIENTE, usuarioValidado.getEmail());
            sesionPendiente.setAttribute(SESION_2FA_CADUCIDAD, Instant.now().plusSeconds(300));
            sesionPendiente.setAttribute(SESION_2FA_SECRETO, usuarioValidado.getSecretoTotpCifrado());
            sesionPendiente.setMaxInactiveInterval(5 * 60);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new LoginResponse(usuarioValidado.getEmail(), rol, true, null));
        }
        limpiarSegundoFactorPendiente(request.getSession(false));
        completarAutenticacion(usuarioValidado, request, response);
        return ResponseEntity.ok(new LoginResponse(usuarioValidado.getEmail(), rol));
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<LoginResponse> verificarSegundoFactor(
            @Valid @RequestBody TwoFactorCodeRequest peticion,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        synchronized (sesion) {
            String email = (String) sesion.getAttribute(SESION_EMAIL_2FA_PENDIENTE);
            Instant caducidad = (Instant) sesion.getAttribute(SESION_2FA_CADUCIDAD);
            if (email == null || caducidad == null || !Instant.now().isBefore(caducidad)) {
                limpiarSegundoFactorPendiente(sesion);
                return ResponseEntity.status(HttpStatus.GONE).build();
            }
            Usuario usuario = authService.buscarPorCorreo(email);
            if (!usuario.isActivada() || usuario.isBloqueada() || !usuario.isTotpActivado()
                    || usuario.getSecretoTotpCifrado() == null
                    || !usuario.getSecretoTotpCifrado().equals(sesion.getAttribute(SESION_2FA_SECRETO))) {
                limpiarSegundoFactorPendiente(sesion);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!totpService.verificarSegundoFactor(usuario, peticion.code())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            limpiarSegundoFactorPendiente(sesion);
            completarAutenticacion(usuario, request, response);
            return ResponseEntity.ok(new LoginResponse(usuario.getEmail(), obtenerRol(usuario)));
        }
    }

    private void limpiarSegundoFactorPendiente(HttpSession sesion) {
        if (sesion != null) {
            sesion.removeAttribute(SESION_EMAIL_2FA_PENDIENTE);
            sesion.removeAttribute(SESION_2FA_CADUCIDAD);
            sesion.removeAttribute(SESION_2FA_SECRETO);
        }
    }

    private void completarAutenticacion(
            Usuario usuarioValidado,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                usuarioValidado.getEmail(),
                null,
                java.util.List.of(new SimpleGrantedAuthority(PREFIJO_ROL + obtenerRol(usuarioValidado))));

        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setMaxInactiveInterval(30 * 60);
        sesionService.registrarSesion(sesion.getId(), usuarioValidado.getEmail());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @GetMapping("/sesion-actual")
    public ResponseEntity<LoginResponse> obtenerSesionActual(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String autoridad = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value != null && value.startsWith(PREFIJO_ROL))
                .findFirst()
                .orElse(null);
        if (autoridad == null || autoridad.length() == PREFIJO_ROL.length()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String rol = autoridad.substring(PREFIJO_ROL.length());
        return ResponseEntity.ok(new LoginResponse(authentication.getName(), rol));
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
