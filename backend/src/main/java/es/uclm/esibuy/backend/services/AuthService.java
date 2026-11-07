package es.uclm.esibuy.backend.services;

import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.backend.dao.UsuarioDAO;
import es.uclm.esibuy.backend.exceptions.AccountPendingException;
import es.uclm.esibuy.backend.exceptions.LoginRateLimitException;
import es.uclm.esibuy.backend.model.Usuario;

@Service
public class AuthService {

    private static final String MENSAJE_ERROR_CREDENCIALES = "Correo o contraseña incorrectos.";

    private final UsuarioDAO usuarioDAO;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AuditoriaService auditoriaService;

    public AuthService(
            UsuarioDAO usuarioDAO,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            AuditoriaService auditoriaService) {
        this.usuarioDAO = usuarioDAO;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.auditoriaService = auditoriaService;
    }

    public Usuario autenticar(String correo, String contrasena, String ip) {
        if (correo == null || contrasena == null) {
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }

        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        try {
            loginAttemptService.reservarIntento(ip, correoNormalizado);
        } catch (LoginRateLimitException exception) {
            auditoriaService.registrarBloqueoLogin(exception.getRetryAfterSeconds());
            throw exception;
        }
        Usuario usuario = usuarioDAO.findByEmail(correoNormalizado)
                .orElse(null);

        if (usuario == null || usuario.getContrasenaHash() == null
                || !passwordEncoder.matches(contrasena, usuario.getContrasenaHash())) {
            int retryAfter = loginAttemptService.registrarFallo(correoNormalizado);
            auditoriaService.registrarIntentoLoginFallido();
            if (retryAfter > 0) {
                auditoriaService.registrarBloqueoLogin(retryAfter);
                throw new LoginRateLimitException(retryAfter);
            }
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }

        if (passwordEncoder.upgradeEncoding(usuario.getContrasenaHash())) {
            usuario.actualizarContrasenaHash(passwordEncoder.encode(contrasena));
            usuarioDAO.save(usuario);
        }

        if (usuario.isBloqueada()) {
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }

        if (!usuario.isActivada()) {
            throw new AccountPendingException();
        }

        loginAttemptService.registrarExito(correoNormalizado);
        return usuario;
    }

    public Usuario buscarPorCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES);
        }
        return usuarioDAO.findByEmail(correo.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException(MENSAJE_ERROR_CREDENCIALES));
    }
}
