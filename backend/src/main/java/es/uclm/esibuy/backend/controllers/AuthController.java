package es.uclm.esibuy.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import es.uclm.esibuy.backend.dto.TwoFactorEnrollmentLoginResponse;
import es.uclm.esibuy.backend.dto.TwoFactorSetupResponse;
import es.uclm.esibuy.backend.dto.TwoFactorCodeRequest;
import es.uclm.esibuy.backend.model.Usuario;
import es.uclm.esibuy.backend.security.ContextoAutenticacion;
import es.uclm.esibuy.backend.security.RolUsuario;
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

    private static final String PREFIJO_ROL = ContextoAutenticacion.PREFIJO_ROL;
    static final String SESION_EMAIL_2FA_PENDIENTE = "ESIBUY_2FA_EMAIL_PENDIENTE";
    static final String SESION_2FA_CADUCIDAD = "ESIBUY_2FA_CADUCIDAD";
    // Nombre de un atributo de sesión; su valor no es una credencial.
    static final String SESION_2FA_SECRETO = "ESIBUY_2FA_SECRETO"; // gitleaks:allow

    static final String SESION_2FA_ALTA = "ESIBUY_2FA_ALTA";
    static final String SESION_3FA_EMAIL = "ESIBUY_3FA_EMAIL";
    static final String SESION_3FA_CADUCIDAD = "ESIBUY_3FA_CADUCIDAD";
    static final String SESION_3FA_TOTP_CIFRADO = "ESIBUY_3FA_TOTP_CIFRADO";

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
        this.totpService = ContextoAutenticacion.requerido(totpService);
        this.emailOtpService = ContextoAutenticacion.requerido(emailOtpService);
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

            ContextoAutenticacion.limpiar();

            HttpSession sesionPendiente = request.getSession(true);

            sesionPendiente.setAttribute(
                    SESION_EMAIL_2FA_PENDIENTE,
                    usuarioValidado.getEmail());

            sesionPendiente.setAttribute(
                    SESION_2FA_CADUCIDAD,
                    ContextoAutenticacion.caducaEn(300));

            sesionPendiente.setAttribute(
                    SESION_2FA_SECRETO,
                    usuarioValidado.getSecretoTotpCifrado());

            sesionPendiente.setAttribute(SESION_2FA_ALTA, necesitaAlta);
            sesionPendiente.setMaxInactiveInterval(5 * 60);

            return RespuestasTercerFactor.aceptado(new LoginResponse(
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
            return RespuestasTercerFactor.noAutenticado();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeAlta(sesion);

            if (usuario == null) {
                return RespuestasTercerFactor.noAutenticado();
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
            return RespuestasTercerFactor.noAutenticado();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeAlta(sesion);

            if (usuario == null) {
                return RespuestasTercerFactor.noAutenticado();
            }

            var confirmacion = totpService.confirmarConfiguracion(
                    usuario, peticion.code());

            boolean codigoEmailEnviado = iniciarTercerFactor(usuario, sesion);
            return RespuestasTercerFactor.aceptado(new TwoFactorEnrollmentLoginResponse(
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

        Object caducidad = sesion.getAttribute(SESION_2FA_CADUCIDAD);

        if (!Boolean.TRUE.equals(sesion.getAttribute(SESION_2FA_ALTA))
                || email == null
                || !ContextoAutenticacion.vigente(caducidad)) {

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
            return RespuestasTercerFactor.noAutenticado();
        }
        synchronized (sesion) {
            String email = (String) sesion.getAttribute(SESION_EMAIL_2FA_PENDIENTE);
            Object caducidad = sesion.getAttribute(SESION_2FA_CADUCIDAD);
            if (email == null || !ContextoAutenticacion.vigente(caducidad)) {
                limpiarSegundoFactorPendiente(sesion);
                return RespuestasTercerFactor.caducado();
            }
            Usuario usuario = authService.buscarPorCorreo(email);
            if (!usuario.isActivada() || usuario.isBloqueada() || !usuario.isTotpActivado()
                    || usuario.getSecretoTotpCifrado() == null
                    || !usuario.getSecretoTotpCifrado().equals(sesion.getAttribute(SESION_2FA_SECRETO))) {
                limpiarSegundoFactorPendiente(sesion);
                return RespuestasTercerFactor.noAutenticado();
            }
            if (!totpService.verificarSegundoFactor(usuario, peticion.code())) {
                return RespuestasTercerFactor.noAutenticado();
            }
            if (RolUsuario.requiereTercerFactor(usuario)) {
                boolean codigoEmailEnviado = iniciarTercerFactor(usuario, sesion);
                return RespuestasTercerFactor.aceptado(new LoginResponse(
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
            return RespuestasTercerFactor.noAutenticado();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeTercerFactor(sesion);
            if (usuario == null) {
                return RespuestasTercerFactor.caducado();
            }

            ResponseEntity<?> error = RespuestasTercerFactor.verificar(
                    emailOtpService,
                    sesion,
                    usuario.getEmail(),
                    peticion.code(),
                    () -> limpiarTercerFactorPendiente(sesion));
            if (error != null) {
                return error;
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
            return RespuestasTercerFactor.noAutenticado();
        }

        synchronized (sesion) {
            Usuario usuario = obtenerUsuarioPendienteDeTercerFactor(sesion);
            if (usuario == null) {
                return RespuestasTercerFactor.caducado();
            }

            return RespuestasTercerFactor.reenviar(
                    emailOtpService,
                    sesion,
                    usuario.getEmail(),
                    () -> limpiarTercerFactorPendiente(sesion));
        }
    }

    private Usuario obtenerUsuarioPendienteDeTercerFactor(HttpSession sesion) {
        String email = (String) sesion.getAttribute(SESION_3FA_EMAIL);
        Object caducidad = sesion.getAttribute(SESION_3FA_CADUCIDAD);
        if (email == null || !ContextoAutenticacion.vigente(caducidad)) {
            limpiarTercerFactorPendiente(sesion);
            return null;
        }

        Usuario usuario = authService.buscarPorCorreo(email);
        if (!usuario.isActivada()
                || usuario.isBloqueada()
                || !usuario.isTotpActivado()
                || usuario.getSecretoTotpCifrado() == null
                || !usuario.getSecretoTotpCifrado().equals(
                        sesion.getAttribute(SESION_3FA_TOTP_CIFRADO))
                || !RolUsuario.requiereTercerFactor(usuario)) {
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
                ContextoAutenticacion.caducaEn(EmailOtpService.DURACION_RETO_SEGUNDOS));
        sesion.setAttribute(SESION_3FA_TOTP_CIFRADO, usuario.getSecretoTotpCifrado());
        sesion.setMaxInactiveInterval(EmailOtpService.DURACION_RETO_SEGUNDOS);
        ContextoAutenticacion.limpiar();

        return RespuestasTercerFactor.iniciar(
                emailOtpService, sesion, usuario.getEmail(), obtenerRol(usuario));
    }

    private void limpiarTercerFactorPendiente(HttpSession sesion) {
        if (sesion != null) {
            sesion.removeAttribute(SESION_3FA_EMAIL);
            sesion.removeAttribute(SESION_3FA_CADUCIDAD);
            sesion.removeAttribute(SESION_3FA_TOTP_CIFRADO);
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
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setMaxInactiveInterval(30 * 60);
        ContextoAutenticacion.marcarInicioSesion(sesion);
        sesionService.registrarSesion(sesion.getId(), usuarioValidado.getEmail());

        var context = ContextoAutenticacion.crear(usuarioValidado.getEmail(), obtenerRol(usuarioValidado));
        ContextoAutenticacion.establecer(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @GetMapping("/sesion-actual")
    public ResponseEntity<LoginResponse> obtenerSesionActual(Authentication authentication) {
        if (authentication == null) {
            return RespuestasTercerFactor.noAutenticado();
        }

        String autoridad = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value != null && value.startsWith(PREFIJO_ROL))
                .findFirst()
                .orElse(null);
        if (autoridad == null || autoridad.length() == PREFIJO_ROL.length()) {
            return RespuestasTercerFactor.noAutenticado();
        }

        String rol = autoridad.substring(PREFIJO_ROL.length());
        return ResponseEntity.ok(new LoginResponse(authentication.getName(), rol));
    }

    private String obtenerRol(Usuario usuario) {
        return RolUsuario.de(usuario);
    }

    private boolean requiereTotpObligatorio(Usuario usuario) {
        return RolUsuario.requiereTotpObligatorio(usuario);
    }

}
