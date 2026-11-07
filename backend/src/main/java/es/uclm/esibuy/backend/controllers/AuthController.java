package es.uclm.esibuy.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.backend.dto.CsrfTokenResponse;
import es.uclm.esibuy.backend.dto.ApiErrorResponse;
import es.uclm.esibuy.backend.dto.LoginRequest;
import es.uclm.esibuy.backend.dto.LoginResponse;
import es.uclm.esibuy.backend.dto.TwoFactorEnrollmentLoginResponse;
import es.uclm.esibuy.backend.dto.TwoFactorSetupResponse;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.exceptions.EmailOtpRateLimitException;
import es.uclm.esibuy.backend.security.SesionAbsolutaFilter;
import es.uclm.esibuy.backend.services.AuthService;
import es.uclm.esibuy.backend.services.EmailOtpService;
import es.uclm.esibuy.backend.services.SesionService;
import es.uclm.esibuy.backend.services.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    private static final String PREFIJO_ROL = "ROLE_";
    static final String SESION_EMAIL_2FA_PENDIENTE = "ESIBUY_2FA_EMAIL_PENDIENTE";
    static final String SESION_2FA_CADUCIDAD = "ESIBUY_2FA_CADUCIDAD";
    // Nombre de un atributo de sesión; su valor no es una credencial.
    static final String SESION_2FA_SECRETO = "ESIBUY_2FA_SECRETO"; // gitleaks:allow

    static final String SESION_2FA_ALTA = "ESIBUY_2FA_ALTA";
    static final String SESION_3FA_EMAIL = "ESIBUY_3FA_EMAIL";
    static final String SESION_3FA_CADUCIDAD = "ESIBUY_3FA_CADUCIDAD";
    static final String SESION_3FA_SECRETO = "ESIBUY_3FA_SECRETO";

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SesionService sesionService;
    private final TotpService totpService;
    private final EmailOtpService emailOtpService;

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository,
            SesionService sesionService,
            TotpService totpService,
            EmailOtpService emailOtpService) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.sesionService = sesionService;
        this.totpService = Objects.requireNonNull(totpService);
        this.emailOtpService = Objects.requireNonNull(emailOtpService);
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
                peticion.email(),
                peticion.password(),
                request.getRemoteAddr());

        String rol = obtenerRol(usuarioValidado);

        boolean necesitaAlta = !usuarioValidado.isTotpActivado()
                && requiereTotpObligatorio(usuarioValidado);

        if (usuarioValidado.isTotpActivado() || necesitaAlta) {
            HttpSession anterior = request.getSession(false);

            if (anterior != null) {
                anterior.invalidate();
            }

            SecurityContextHolder.clearContext();

            HttpSession sesionPendiente = request.getSession(true);

            sesionPendiente.setAttribute(
                    SESION_EMAIL_2FA_PENDIENTE,
                    usuarioValidado.getEmail());

            sesionPendiente.setAttribute(
                    SESION_2FA_CADUCIDAD,
                    Instant.now().plusSeconds(300));

            sesionPendiente.setAttribute(
                    SESION_2FA_SECRETO,
                    usuarioValidado.getSecretoTotpCifrado());

            sesionPendiente.setAttribute(SESION_2FA_ALTA, necesitaAlta);
            sesionPendiente.setMaxInactiveInterval(5 * 60);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new LoginResponse(
                            usuarioValidado.getEmail(),
                            rol,
                            true,
                            null,
                            necesitaAlta));
        }

        limpiarSegundoFactorPendiente(request.getSession(false));
        completarAutenticacion(usuarioValidado, request, response);

        return ResponseEntity.ok(
                new LoginResponse(usuarioValidado.getEmail(), rol));
    }

    @PostMapping("/login/2fa/configurar")
    public ResponseEntity<TwoFactorSetupResponse> configurarAltaObligatoria(
            HttpServletRequest request) {

        HttpSession sesion = request.getSession(false);

        if (sesion == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeAlta(sesion);

            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String secreto = totpService.generarSecreto();

            totpService.guardarSecretoPendiente(usuario, secreto);

            String uri = totpService.crearUriAlta(
                    usuario.getEmail(), secreto);

            return ResponseEntity.ok(
                    new TwoFactorSetupResponse(
                            uri,
                            totpService.generarQrDataUrl(uri),
                            false));
        }
    }

    @PostMapping("/login/2fa/confirmar-alta")
    public ResponseEntity<TwoFactorEnrollmentLoginResponse> confirmarAltaObligatoria(
            @Valid @RequestBody TwoFactorCodeRequest peticion,
            HttpServletRequest request) {

        HttpSession sesion = request.getSession(false);

        if (sesion == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeAlta(sesion);

            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            var confirmacion = totpService.confirmarConfiguracion(
                    usuario, peticion.code());

            boolean codigoEmailEnviado = iniciarTercerFactor(usuario, sesion);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(
                    new TwoFactorEnrollmentLoginResponse(
                            new LoginResponse(
                                    usuario.getEmail(),
                                    obtenerRol(usuario),
                                    false,
                                    null,
                                    false,
                                    true,
                                    codigoEmailEnviado),
                            confirmacion.backupCodes()));
        }
    }

    private Usuario obtenerUsuarioPendienteDeAlta(HttpSession sesion) {

        String email = (String) sesion.getAttribute(
                SESION_EMAIL_2FA_PENDIENTE);

        Instant caducidad = (Instant) sesion.getAttribute(
                SESION_2FA_CADUCIDAD);

        if (!Boolean.TRUE.equals(sesion.getAttribute(SESION_2FA_ALTA))
                || email == null
                || caducidad == null
                || !Instant.now().isBefore(caducidad)) {

            limpiarSegundoFactorPendiente(sesion);
            return null;
        }

        Usuario usuario = authService.buscarPorCorreo(email);

        if (!usuario.isActivada()
                || usuario.isBloqueada()
                || usuario.isTotpActivado()
                || !requiereTotpObligatorio(usuario)) {

            limpiarSegundoFactorPendiente(sesion);
            return null;
        }

        return usuario;
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
            if (requiereTotpObligatorio(usuario)) {
                boolean codigoEmailEnviado = iniciarTercerFactor(usuario, sesion);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new LoginResponse(
                                usuario.getEmail(),
                                obtenerRol(usuario),
                                false,
                                null,
                                false,
                                true,
                                codigoEmailEnviado));
            }
            limpiarSegundoFactorPendiente(sesion);
            completarAutenticacion(usuario, request, response);
            return ResponseEntity.ok(new LoginResponse(usuario.getEmail(), obtenerRol(usuario)));
        }
    }

    @PostMapping("/login/3fa/verificar")
    public ResponseEntity<?> verificarTercerFactor(
            @Valid @RequestBody TwoFactorCodeRequest peticion,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeTercerFactor(sesion);
            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            EmailOtpService.ResultadoVerificacion resultado = emailOtpService.verificar(
                    sesion,
                    usuario.getEmail(),
                    peticion.code());
            if (resultado == EmailOtpService.ResultadoVerificacion.INVALIDO) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiErrorResponse("El código de verificación no es válido."));
            }
            if (resultado == EmailOtpService.ResultadoVerificacion.BLOQUEADO) {
                limpiarTercerFactorPendiente(sesion);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", Integer.toString(EmailOtpService.DURACION_RETO_SEGUNDOS))
                        .body(new ApiErrorResponse(
                                "Se ha alcanzado el límite de intentos. Inicia sesión de nuevo más tarde."));
            }
            if (resultado == EmailOtpService.ResultadoVerificacion.CADUCADO) {
                limpiarTercerFactorPendiente(sesion);
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            limpiarTercerFactorPendiente(sesion);
            completarAutenticacion(usuario, request, response);
            return ResponseEntity.ok(new LoginResponse(usuario.getEmail(), obtenerRol(usuario)));
        }
    }

    @PostMapping("/login/3fa/reenviar")
    public ResponseEntity<?> reenviarTercerFactor(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeTercerFactor(sesion);
            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            try {
                EmailOtpService.ResultadoReenvio resultado = emailOtpService.reenviar(
                        sesion,
                        usuario.getEmail());
                return switch (resultado.estado()) {
                    case ENVIADO -> ResponseEntity.noContent().build();
                    case ESPERA -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", Integer.toString(resultado.segundosEspera()))
                            .body(new ApiErrorResponse(
                                    "Espera antes de solicitar otro código de verificación."));
                    case LIMITE -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiErrorResponse(
                                    "Has alcanzado el límite de envíos para este inicio de sesión."));
                    case CADUCADO -> {
                        limpiarTercerFactorPendiente(sesion);
                        yield ResponseEntity.status(HttpStatus.GONE).build();
                    }
                };
            } catch (EmailOtpRateLimitException exception) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", Integer.toString(exception.getRetryAfterSeconds()))
                        .body(new ApiErrorResponse(exception.getMessage()));
            } catch (RestClientException | IllegalStateException exception) {
                LOGGER.error("No se pudo enviar el código del tercer factor por correo.", exception);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ApiErrorResponse(
                                "No se pudo enviar el código. Inténtalo de nuevo más tarde."));
            }
        }
    }

    private Usuario obtenerUsuarioPendienteDeTercerFactor(HttpSession sesion) {
        String email = (String) sesion.getAttribute(SESION_3FA_EMAIL);
        Instant caducidad = (Instant) sesion.getAttribute(SESION_3FA_CADUCIDAD);
        if (email == null || caducidad == null || !Instant.now().isBefore(caducidad)) {
            limpiarTercerFactorPendiente(sesion);
            return null;
        }

        Usuario usuario = authService.buscarPorCorreo(email);
        if (!usuario.isActivada()
                || usuario.isBloqueada()
                || !usuario.isTotpActivado()
                || usuario.getSecretoTotpCifrado() == null
                || !usuario.getSecretoTotpCifrado().equals(
                        sesion.getAttribute(SESION_3FA_SECRETO))
                || !requiereTotpObligatorio(usuario)) {
            limpiarTercerFactorPendiente(sesion);
            return null;
        }
        return usuario;
    }

    private boolean iniciarTercerFactor(Usuario usuario, HttpSession sesion) {
        limpiarSegundoFactorPendiente(sesion);
        emailOtpService.limpiar(sesion);
        sesion.setAttribute(SESION_3FA_EMAIL, usuario.getEmail());
        sesion.setAttribute(
                SESION_3FA_CADUCIDAD,
                Instant.now().plusSeconds(EmailOtpService.DURACION_RETO_SEGUNDOS));
        sesion.setAttribute(SESION_3FA_SECRETO, usuario.getSecretoTotpCifrado());
        sesion.setMaxInactiveInterval(EmailOtpService.DURACION_RETO_SEGUNDOS);
        SecurityContextHolder.clearContext();

        try {
            emailOtpService.iniciar(sesion, usuario.getEmail());
            return true;
        } catch (EmailOtpRateLimitException exception) {
            emailOtpService.limpiar(sesion);
            LOGGER.warn("Se ha alcanzado el límite global de envíos para el tercer factor.");
            return false;
        } catch (RestClientException | IllegalStateException exception) {
            emailOtpService.limpiar(sesion);
            LOGGER.error("No se pudo iniciar el tercer factor por correo para el rol {}.",
                    obtenerRol(usuario),
                    exception);
            return false;
        }
    }

    private void limpiarTercerFactorPendiente(HttpSession sesion) {
        if (sesion != null) {
            sesion.removeAttribute(SESION_3FA_EMAIL);
            sesion.removeAttribute(SESION_3FA_CADUCIDAD);
            sesion.removeAttribute(SESION_3FA_SECRETO);
            emailOtpService.limpiar(sesion);
        }
    }

    private void limpiarSegundoFactorPendiente(HttpSession sesion) {
        if (sesion != null) {
            sesion.removeAttribute(SESION_EMAIL_2FA_PENDIENTE);
            sesion.removeAttribute(SESION_2FA_CADUCIDAD);
            sesion.removeAttribute(SESION_2FA_SECRETO);
            sesion.removeAttribute(SESION_2FA_ALTA);
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
        sesion.setAttribute(SesionAbsolutaFilter.ATRIBUTO_SESION_AUTENTICADA_EN, Instant.now());
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

    private boolean requiereTotpObligatorio(Usuario usuario) {
        return usuario instanceof Vendedor
                || usuario instanceof Administrador;
    }

}
